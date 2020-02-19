# NUV: Network Configuration Update Verification
=============================================================================
=== Overview === <br>
NUV can address the lack of knowledge problem and it is able to infer the impacted queries to speed up the verification of network configuration updates. <br>
NUV is intermediate tool between the user who wants to verify the network behavior and existing control plane verification tools. The tool alone does not focus on verifying reachability or any other network property. <br>
 
=== Inputs and outputs === <br>
-The inputs of NUV are the network configuration files and the updated network configuration files. <br>
-The outputs of NUV are the impacted queries for the verification tools. <br>

=== PREREQUISITES ===
- ant
- Batfish (http://www.batfish.org) 
- Java JDK (version 8 or newer)
- ARC -- the interface to batfish and the data structure to store configuration 
  information. <br>

=== Basic Usage ====

- Create a directory containing a snapshot of the configurations from all routers in the network whose control plane you want to analyze. Example configurations for toy networks are included in the directory of Fullmesh-exmaples.zip, Fattree-exmaples.zip, Ring-exmaples.zip, CAMPUS1.zip and CAMPUS2.zip. The languages of input configurations can be defined by multiple vendors like Juniper, Arista, Cisco and
so on.

- The source code is in the nuv directory. User can download the souce code and import the code to Eclipse IDE. <br>
   Launch Eclipse IDE and select 'Import' from 'File' menu. <br>
   In the displayed 'Import' dialog, expand the 'General' folder. Select 'Existing' Projects into Workspace' and click 'Next'. <br>
   This will display the 'Import Projects' dialog box. Choose 'select archive file' option and click 'Browse'. <br>
   Navigate to the folder of the exported file. Select the file and click 'Open'. <br>
   In the 'Import Projects' dialog, ensure that browsed path is displayed. Click 'Finish'. <br>
   Ensure that the imported project is displayed in the Eclipse IDE. <br>

- We also leverage the Batfish parser to translate the configurations into vendor-agnostic reprenstation. Usrs should download the souce code via the website (http://www.batfish.org). <br>

- After the source code is added to Eclipse IDE, users can compile, build and run our program. <br>

- Note that, our main function is in the DriverDiff.java. Usrs should click the file to open the 'edit configuration' dialog.  And then put the 'argument' with '-configs <CONFIGS-DIR> -configs2  <CONFIGS-DIR2>'. <br>
CONFIGS-DIR is the path to a directory containing the orignal configuration files for all devices in the network, and CONFIGS-DIR2 is the path to a directory containing the update configuration files for all devices in the network.<br>
- After the program stop running, the outputs on the consle window include the impacted queries that should be verifed due to configuration update. Then users can download the state-of-the–art tool Minesweeper to verify these queries. 

Users can  also download the Verf_lib and Verf.jar to try exmpale configurations. <br>
The introduction of example configurtion updates (e.g., CAMPUS) is coming. 




