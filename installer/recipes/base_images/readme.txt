Simple setup for creating docker base images. It should create 3 images - base os, base jdk and loadBalancer

To perform follow this scenario:
================================

-> Copy your sapjvm package (linux, rpm) into the resource/base_jdk/java
-> ./install.sh -r base_images buildImages


or alternatively:
-> ./install.sh -r base_images createImagesStructure
-> cd work/output_images/ybase
-> ./build-images.sh


Additionaly you can create solr and zookeeper images:
=====================================================
-> ./install.sh -r base_images buildAdditionalImages


or alternatively:
-> ./install.sh -r base_images createAdditionalImagesStructure
-> cd work/output_images/ybaseadditonal
-> ./build-images.sh


customize the os image
===============================
-> adjust the property values under /resources/base_os/default.properties (this can be done also inside the recipe, where you can set these properties for any image you define there)
-> rebuild the image following the steps described above 
