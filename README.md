# Cordite Template

A template that uses Cordform to spin up a local network of nodes with Cordite installed.

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

There are two ways to interact with Cordite nodes:

* Using a regular `CordaRPCOps` object
* Exposing the `LedgerApi`/`DaoApi`/`MeteringApi` services via Braid

We have written two clients showing the two approaches:

* `DglClientRpc`
* `DglClientBraid`
