Simple setup with Platform only showing how to use mod_cluster based load balancer

Prerequisite
============
Make sure you have the base image installed, if not you can generate the default one following the description under /recipes/base_images/readme.txt.
(the default base image name is ybase_jdk, based on centos and sapjvm, you have to provide).
Instead of generating this image you can select another one by overriding the property 'baseImage; inside your recipe, for your platform/hsql/solr image definition, e.g.:
 property 'baseImage',  'anapsix/alpine-java:8_jdk'

You need a Docker image which encapsulates Selenium env, Browser on which you want to test and other testing tools like VNC viewer to debug test progress inside the image.
We recommend selenium/standalone-chrome-debug:3.0.1-germanium image.
You can install it using command:
docker run -i --rm selenium/standalone-chrome-debug:3.1.0-astatine
During the test you can watch it run through VNC viewer on port 5900. When you are prompted for the password it is 'secret'.


To perform follow this scenario:
================================

-> ./install.sh -r test_rolling_update_on_mod_cluster buildImages
-> ./install.sh -r test_rolling_update_on_mod_cluster -i rollingUpdateTest
