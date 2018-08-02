![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

# Cordite Template

A template that uses Cordfrom to spin up a local network of nodes with Cordite installed.

## Running the Cordite nodes

* Clone the template:
    
      git clone https://github.com/joeldudleyr3/cordite-template.git

* Move into the template directory:

      cd cordite-template

* Deploy the nodes with Cordite installed:

      ./gradlew deployNodes (Unix)

      gradlew.bat deployNodes (Windows)
    
* Run the nodes:

      build/nodes/runnodes (Unix)
      
      build\nodes\runnodes.bat (Windows)

## Interacting with the Cordite nodes

* Run the `DglRpcClient` to issue and transfer Cordite tokens around the network