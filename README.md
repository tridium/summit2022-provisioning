<!---
   @author     Tim Urenda
   @creation   22 March 2022
--->

# Developing Custom Provisioning Modules

This is an example module that implements a custom provisioning step, configuration of that step in workbench, and unit tests for that step.

You will need to set up your certificate in order to compile and run this code, and set the `certAlias` value in the `build.gradle` file.
See the developer documentation for information on how to set up your signing certificate.

Also included is a zip file containing the Javadoc information for the provisioning module that includes the `ProvisioningConnectionUtil` class.
Tridium is in the process of promoting this to the public API, but you can refer to this documentation to use that class before the promotion is released.
Start by loading `index.html` into your browser.