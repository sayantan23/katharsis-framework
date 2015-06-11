# katharsis-servlet

Generic Servlet Adapter of Katharsis JSON:API middleware library.

Introduction
============
This module aims to provide a generic invoker module for
Katharsis JSON:API middleware library (https://github.com/katharsis-project/katharsis-core).
This module can be used in simple servlet or filter,
servlet-based application framework such as Spring Framework,
or even non-ServletAPI-based frameworks such as Portal/Portlet, Wicket, etc.

How to use this in my Servlet?
==============================

This module provides an abstract class, [AbstractKatharsisServlet.java](src/main/java/com/github/woonsan/katharsis/servlet/AbstractKatharsisServlet.java). Basically you need to override the following method at least:

    abstract protected KatharsisInvoker createKatharsisInvoker();

Or even simpler, you can simply use or extend [SimpleKatharsisServlet.java](src/main/java/com/github/woonsan/katharsis/servlet/SimpleKatharsisServlet.java) instead.

How to use this in my Servlet Filter?
=====================================

This module provides an abstract class, [AbstractKatharsisFilter.java](src/main/java/com/github/woonsan/katharsis/servlet/AbstractKatharsisFilter.java). Basically you need to override the following method at least as well:

    abstract protected KatharsisInvoker createKatharsisInvoker();

Or even simpler, you can simply use or extend [SimpleKatharsisFilter.java](src/main/java/com/github/woonsan/katharsis/servlet/SimpleKatharsisFilter.java) instead.

Demo in a Web Application?
==========================

Please run the following command in this project root folder:

    $ mvn -Prun clean verify

Visit [http://localhost:8080/katharsis/](http://localhost:8080/katharsis/) and test out each JSON API link.
