![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

# Cordite Template

Welcome to the Cordite template. This is a template that uses Cordfrom to spin up a local network of nodes with Cordite 
installed.

## Instructions

* Clone the template:
    
      git clone https://github.com/joeldudleyr3/cordite-template.git

* Move into the template directory:

      cd cordite-template

* Deploy the nodes with Cordite installed:

      ./gradlew deployNodes (Unix)

      gradlew.bat deployNodes (Windows)
    
* Run the nodes:

      build/nodes/runnodes
