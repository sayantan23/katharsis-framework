package io.katharsis.meta.model;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.katharsis.core.internal.utils.PreconditionUtil;
import io.katharsis.resource.annotations.JsonApiResource;
import io.katharsis.resource.annotations.JsonApiToMany;
import io.katharsis.resource.annotations.JsonApiToOne;

@JsonApiResource(type = "meta/dataObject")
public abstract class MetaDataObject extends MetaType {

	private static final MetaAttributeFinder DEFAULT_ATTRIBUTE_FINDER = new MetaAttributeFinder() {

		@Override
		public MetaAttribute getAttribute(MetaDataObject meta, String name) {
			return meta.getAttribute(name);
		}
	};

	private static final MetaAttributeFinder SUBTYPE_ATTRIBUTE_FINDER = new MetaAttributeFinder() {

		@Override
		public MetaAttribute getAttribute(MetaDataObject meta, String name) {
			return meta.findAttribute(name, true);
		}
	};
	
	@JsonApiToMany(opposite = "superType")
	private Set<MetaDataObject> subTypes = new HashSet<>();

	@JsonApiToOne(opposite = "subTypes")
	private MetaDataObject superType;

	@JsonIgnore
	private Map<String, MetaAttribute> attrMap = null;

	@JsonApiToMany
	private List<MetaAttribute> attributes = null;

	@JsonApiToMany
	private List<MetaAttribute> declaredAttributes = null;

	@SuppressWarnings("unchecked")
	@JsonIgnore
	private List<MetaDataObject>[] subTypesCache = new List[4];

	@JsonApiToOne
	private MetaKey primaryKey;

	@JsonApiToMany
	private Set<MetaKey> declaredKeys = new HashSet<>();

	@JsonApiToMany
	private Set<MetaInterface> interfaces = new HashSet<>();

	@JsonIgnore
	// TODO
	public MetaAttribute getVersionAttribute() {
		for (MetaAttribute attr : getAttributes()) {
			if (attr.isVersion())
				return attr;
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private void clearCache() {
		subTypesCache = new List[4];
		attributes = null;
		declaredAttributes = null;
		attrMap = null;
	}

	public List<? extends MetaAttribute> getAttributes() {
		setupCache();
		return attributes;
	}

	public List<? extends MetaAttribute> getDeclaredAttributes() {
		setupCache();
		return declaredAttributes;
	}

	public MetaAttribute getAttribute(String name) {
		setupCache();
		MetaAttribute attr = attrMap.get(name);
		PreconditionUtil.assertNotNull(getName() + "." + name, attr);
		return attr;
	}

	public MetaAttributePath resolvePath(List<String> attrPath, boolean includeSubTypes) {
		MetaAttributeFinder finder = includeSubTypes ? SUBTYPE_ATTRIBUTE_FINDER : DEFAULT_ATTRIBUTE_FINDER;
		return resolvePath(attrPath, finder);
	}

	public MetaAttributePath resolvePath(List<String> attrPath) {
		return resolvePath(attrPath, true);
	}

	public MetaAttributePath resolvePath(List<String> attrPath, MetaAttributeFinder finder) {
		setupCache();
		if (attrPath == null || attrPath.isEmpty())
			throw new IllegalArgumentException("invalid attribute path '" + attrPath + "'");
		LinkedList<MetaAttribute> list = new LinkedList<>();

		MetaDataObject currentMdo = this;
		int i = 0;
		while (i < attrPath.size()) {
			String pathElementName = attrPath.get(i);
			MetaAttribute pathElement = finder.getAttribute(currentMdo, pathElementName);
			if (i < attrPath.size() - 1 && pathElement.getType() instanceof MetaMapType) {
				MetaMapType mapType = (MetaMapType) pathElement.getType();

				// next "attribute" is the key within the map
				String keyString = attrPath.get(i + 1);

				MetaMapAttribute keyAttr = new MetaMapAttribute(mapType, pathElement, keyString);
				list.add(keyAttr);
				i++;
				MetaType valueType = mapType.getValueType();
				currentMdo = nextPathElement(valueType, i, attrPath);
			} else {
				list.add(pathElement);
				currentMdo = nextPathElement(pathElement.getType(), i, attrPath);
			}
			i++;
		}

		return new MetaAttributePath(list);
	}

	private MetaDataObject nextPathElement(MetaType pathElementType, int i, List<String> pathElements) {
		if (i == pathElements.size() - 1) {
			return null;
		} else {
			if (!(pathElementType instanceof MetaDataObject)) {
				throw new IllegalArgumentException("failed to resolve path " + pathElements);
			}
			return pathElementType.asDataObject();
		}
	}

	public MetaAttribute findAttribute(String name, boolean includeSubTypes) {
		if (hasAttribute(name)) {
			return getAttribute(name);
		}

		if (includeSubTypes) {
			List<? extends MetaDataObject> transitiveSubTypes = getSubTypes(true, true);
			for (MetaDataObject subType : transitiveSubTypes) {
				if (subType.hasAttribute(name)) {
					return subType.getAttribute(name);
				}
			}
		}

		throw new IllegalStateException("attribute " + name + " not found in " + getName());
	}

	public boolean hasAttribute(String name) {
		setupCache();
		return attrMap.containsKey(name);
	}

	public MetaDataObject getSuperType() {
		return superType;
	}

	public List<MetaDataObject> getSubTypes(boolean transitive, boolean self) {
		int cacheIndex = (transitive ? 2 : 0) | (self ? 1 : 0);

		List<MetaDataObject> cached = subTypesCache[cacheIndex];
		if (cached != null) {
			return cached;
		} else {
			ArrayList<MetaDataObject> types = computeSubTypes(transitive, self);
			List<MetaDataObject> unmodifiableList = Collections.unmodifiableList(types);
			subTypesCache[cacheIndex] = unmodifiableList;
			return unmodifiableList;
		}
	}

	private ArrayList<MetaDataObject> computeSubTypes(boolean transitive, boolean self) {
		ArrayList<MetaDataObject> types = new ArrayList<>();

		if (self && (!isAbstract() || !subTypes.isEmpty()))
			types.add(this);

		for (MetaDataObject subType : subTypes) {
			if (!subType.isAbstract() || !subType.getSubTypes().isEmpty())
				types.add(subType);
			if (transitive) {
				types.addAll(subType.getSubTypes(true, false));
			}
		}
		return types;
	}

	@JsonIgnore
	public boolean isAbstract() {
		return Modifier.isAbstract(getImplementationClass().getModifiers());
	}

	public Set<MetaDataObject> getSubTypes() {
		return subTypes;
	}

	public Set<MetaInterface> getInterfaces() {
		return interfaces;
	}

	public void setInterfaces(Set<MetaInterface> interfaces) {
		this.interfaces = interfaces;
	}

	public MetaKey getPrimaryKey() {
		if (primaryKey == null && superType != null) {
			return superType.getPrimaryKey();
		}
		return primaryKey;
	}

	public Set<MetaKey> getDeclaredKeys() {
		return declaredKeys;
	}

	public void setDeclaredKeys(Set<MetaKey> declaredKeys) {
		this.declaredKeys = declaredKeys;
	}

	public void setDeclaredAttributes(List<MetaAttribute> declaredAttributes) {
		this.declaredAttributes = declaredAttributes;
	}

	public void setAttributes(List<MetaAttribute> attributes) {
		this.attributes = attributes;
	}

	public void setSubTypes(Set<MetaDataObject> subTypes) {
		this.subTypes = subTypes;
	}

	public void setPrimaryKey(MetaKey key) {
		this.primaryKey = key;
		addDeclaredKey(key);
	}

	public void addDeclaredKey(MetaKey key) {
		declaredKeys.add(key);
	}

	public void addSubType(MetaDataObject subType) {
		this.subTypes.add(subType);
		this.clearCache();
	}

	public void setSuperType(MetaDataObject superType) {
		this.superType = superType;
		if (superType != null) {
			superType.addSubType(this);
		}
	}

	private void setupCache() {
		if (this.declaredAttributes == null) {
			List<MetaAttribute> newDeclaredAttributes = new ArrayList<>();
			List<MetaAttribute> newAttributes = new ArrayList<>();

			if (superType != null) {
				newAttributes.addAll(superType.getAttributes());
			}

			for (MetaElement child : getChildren()) {
				if (child instanceof MetaAttribute) {
					MetaAttribute attr = (MetaAttribute) child;
					newDeclaredAttributes.add(attr);
					newAttributes.add(attr);

				}
			}

			this.attributes = Collections.unmodifiableList(newAttributes);
			this.declaredAttributes = Collections.unmodifiableList(newDeclaredAttributes);
		}
		if (this.attrMap == null) {
			HashMap<String, MetaAttribute> newAttrMap = new HashMap<>();
			if (superType != null) {
				newAttrMap.putAll(superType.attrMap);
			}
			for (MetaAttribute attr : declaredAttributes) {
				newAttrMap.put(attr.getName(), attr);
			}
			this.attrMap = Collections.unmodifiableMap(newAttrMap);
		}
	}
}