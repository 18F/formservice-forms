# README.md
## FormService Forms

This is the main repository for the FormService forms.  There are companion repositories for the FormService / Forms as a Service (FaaS) microservice, Tools, and Infrastructure as Code (IaC).

## Repository Structure

The FormService Forms GitHub Repository contains the json representation of the form defintions for the FormService.  In addition, tools that support deployment of the forms are also included in this repository.  Each form definition is in a separate folder.


### HUD-903_1-FHEO

This folder contains the latest definition for the Fair Housing and Equal Opportunity (FHEO) form.


### HUD-20755-IDIS

This folder contains the latest definition for the IDIS Online Access Request form.


### HUD-50075-MTW

This folder contains the latest definition for the Moving to Work (MTW) form.


### IRS-8821

This folder contains the latest definition for the IRS-8821 form.


### Form Deployer

The FormDeployer tool is a simple command line tool that can be used to deploy a form.io form to another environment.  The user provides a Form.io form definition file path, an authentication token for the destination form definition server, and the full path to the target environment, and the tool deploys the form to the target environment.  If the form does not exist, it will be created, otherwise, it will be udpated.  The tool will primarily be used as part of the CI/CD process, but can be used in a standalone fashion if needed.

To run:
* Pull down the FormDeployer directory
* Run the following command, replacing 'form_file_path', 'dest_auth_key', 'dest_path' with appropriate values

C:\dev\FormDeployer>gradlew run --args="form_file_path dest_auth_key dest_path"

Here is an example call (auth key not provided):

C:\dev\FormDeployer>gradlew run --args="C:\formDefinitionToPublish.json xxxxxxxxxxxxxxxxx https://dev-portal.fs.gsa.gov/dev"

When the tool completes, the form should be deployed to the destination stage.  

*Development Note: In order for the tests in this tool to run successfully, create a "formiokeys.env" file in the test/resources directory, and populate the following value with an auth key: FORMIO_DEV_API_KEY=xxxxxxxxxxxxxxxxxxxxxx*