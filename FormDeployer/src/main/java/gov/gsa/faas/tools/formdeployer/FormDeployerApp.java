package gov.gsa.faas.tools.formdeployer;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import gov.gsa.faas.tools.formdeployer.type.FormDefinition;
import gov.gsa.faas.tools.formdeployer.type.Role;
import gov.gsa.faas.tools.formdeployer.type.SingleAccess;

public class FormDeployerApp {

    private HttpClient formIoClient = null;
    private String targetEnvAuthToken = null;
    private String targetEnvPath = null;
    private ObjectMapper mapper = null;

    public FormDeployerApp(String targetEnvAuthToken, String targetEnvPath) {
        this.formIoClient = HttpClient.newBuilder().proxy(ProxySelector.getDefault()).build();
        this.targetEnvAuthToken = targetEnvAuthToken;
        this.targetEnvPath = targetEnvPath;
        this.mapper = new ObjectMapper();
    }

    
    /** 
     * Read the form at the provided path into a FormDefinition structure
     * 
     * @param formDefinitionPath The full path to the Form.io form definition json file
     * @return FormDefinition The FormDefinition object populated from the file at the path provided
     * @throws IOException if the file cannot be read
     */
    public FormDefinition readFormDefinitionJson(String formDefinitionPath) throws IOException {

        Path path = FileSystems.getDefault().getPath(formDefinitionPath);
        String formDefinitionContent = Files.readString(path, StandardCharsets.US_ASCII);

        // read json into JsonNode using Jackson ObjectMapper
        JsonNode rootNode = null;
        rootNode = mapper.readTree(formDefinitionContent);

        return new FormDefinition(rootNode);
    }

    
    /** 
     * Queries the form.io destination location to get the ID for a given roleTitle
     * 
     * @param roleTitle The role name in form.io for which to retrieve the ID
     * @return String The ID
     * @throws URISyntaxException if the path cannot be created
     * @throws IOException if the form.io destination location cannot be queried, or the results cannot be parsed
     * @throws InterruptedException if the rest call to the destination location is interrupted
     */
    public String getIdByRoleTitle(String roleTitle) throws URISyntaxException, IOException, InterruptedException {

        URI roleUri = new URI(targetEnvPath + "/role");

        HttpRequest roleRequest = HttpRequest.newBuilder().header("x-token", targetEnvAuthToken).uri(roleUri).GET()
                .build();

        HttpResponse<String> roleResponse = formIoClient.send(roleRequest, HttpResponse.BodyHandlers.ofString());

        // read json into JsonNode using Jackson ObjectMapper
        List<Role> roleList = mapper.readValue(roleResponse.body(), new TypeReference<List<Role>>() {
        });

        Iterator<Role> roleIterator = roleList.iterator();
        boolean roleFound = false;
        String roleId = "";

        while (!roleFound && roleIterator.hasNext()) {
            Role currentRole = roleIterator.next();
            if (currentRole.getTitle().equalsIgnoreCase(roleTitle)) {
                roleId = currentRole.getId();
                roleFound = true;
            }
        }

        return roleId;
    }

    
    /** 
     * Create the form.io Json where the provided roleId has "create_own" submission access
     * 
     * @param roleId The roleId to use within the submission access json
     * @return String The string representation of the submission access json
     * @throws JsonProcessingException if the submission access json representation cannot be created
     */
    public String createSubmissionAccessJsonWithCreateOwn(String roleId) throws JsonProcessingException {
        List<String> roleIdArray = Arrays.asList(roleId.split(","));
        List<String> emptyRoles = new ArrayList<>();
        emptyRoles.add("");
        List<SingleAccess> accessList = new ArrayList<>();
        accessList.add(new SingleAccess(roleIdArray, "create_own"));
        accessList.add(new SingleAccess(emptyRoles, "create_all"));
        accessList.add(new SingleAccess(emptyRoles, "read_own"));
        accessList.add(new SingleAccess(emptyRoles, "read_all"));
        accessList.add(new SingleAccess(emptyRoles, "update_own"));
        accessList.add(new SingleAccess(emptyRoles, "update_all"));
        accessList.add(new SingleAccess(emptyRoles, "delete_own"));
        accessList.add(new SingleAccess(emptyRoles, "delete_all"));
        accessList.add(new SingleAccess(emptyRoles, "team_read"));
        accessList.add(new SingleAccess(emptyRoles, "team_write"));
        accessList.add(new SingleAccess(emptyRoles, "team_admin"));
        return mapper.writeValueAsString(accessList);
    }

    
    /** 
     * Creates a FormDefinition object from the provided formDef that contains the provided submissionAccess
     * 
     * @param formDef The FormDefinition base
     * @param submissionAccessJson The submissionAccess to add to the formDef base
     * @return FormDefinition The FormDefintion object created from the formDef that includes the submissionAccessJson
     * @throws JsonMappingException If the submissionAccessJson is malformed
     * @throws JsonProcessingException If the FormDefinition object cannot be created
     */
    public FormDefinition addSubmissionAccessToFormDefinition(FormDefinition formDef, String submissionAccessJson)
            throws JsonProcessingException {

        ObjectNode o = ((ObjectNode) formDef.getFormDefinitionNode()).set("submissionAccess", mapper.readTree(submissionAccessJson));
        return new FormDefinition(o);
    }

    
    /** 
     * Publishes the FormDefinition to the target environment, creating the form if it doesn't exist
     * or updating the form if it does exist
     * 
     * @param formDef The FormDefinition to publish
     * @return String A status value.  "error" if there was a problem, "created" if the form was created, and "updated" if the form already existed and was modified
     * @throws IOException if the form.io destination location cannot be reached
     * @throws InterruptedException if the rest call to the destination location is interrupted
     * @throws URISyntaxException if the path to create or update the form cannot be formed
     */
    public String publishFormJson(FormDefinition formDef) throws IOException, InterruptedException, URISyntaxException {

        URI postUri = new URI(targetEnvPath + "/form");
        URI putUri = new URI(targetEnvPath + "/" + formDef.getFormName());
        String publishResponse = "error";

        // try to put. If form does not exist, it will fail, and we should try to create
        // form using post
        HttpRequest formUpdateRequest = HttpRequest.newBuilder().header("x-token", targetEnvAuthToken)
                .header("Content-Type", "application/json").uri(putUri)
                .PUT(BodyPublishers.ofString(formDef.getFormDefinitionNode().toPrettyString())).build();
        HttpResponse<String> formUpdateResponse = formIoClient.send(formUpdateRequest,
                HttpResponse.BodyHandlers.ofString());

        if (formUpdateResponse.statusCode() == 200) {
            publishResponse = "updated";
        } else {
            // Try to create the form using post
            HttpRequest formCreateRequest = HttpRequest.newBuilder().header("x-token", targetEnvAuthToken)
                    .header("Content-Type", "application/json").uri(postUri)
                    .POST(BodyPublishers.ofString(formDef.getFormDefinitionNode().toPrettyString())).build();
            HttpResponse<String> formCreateResponse = formIoClient.send(formCreateRequest,
                    HttpResponse.BodyHandlers.ofString());
            // Test response to make sure something happenned
            if (formCreateResponse.statusCode() == 201) {
                publishResponse = "created";
            }
        }

        return publishResponse;
    }

    /**
     * Takes a given form defintion json file and publishes it to the Target environment.
     * Will create the form if it doesn't already exist, or will update the form if it does exist.
     * 
     * @param args Three arguments are required:
     *             <ol>
     *             <li>Form Definition Path: The full path to the Form.io form definition json file</li>
     *             <li>Target Environment Auth Token: The x-token value to be provided in the header to authenticate to the Form.io form definition json rest server</li>
     *             <li>Target Environment Path: The full path to the target environment</li>
     *             </ol>
     * @throws JsonProcessingException
     */
    public static void main(String[] args) {
        if (args == null || args.length != 3) {
            System.out.println("Three arguments are required.\n"
                    + "Form Definition Path: The full path to the Form.io form definition json file \n"
                    + "Target Environment Auth Token: The x-token value to be provided in the header to authenticate to the Form.io form definition json rest server \n"
                    + "Target Environment Path: The full path to the target environment \n");
        } else {
            String formDefinitionPath = args[0];
            String targetEnvAuthToken = args[1];
            String targetEnvPath = args[2];
            FormDeployerApp formDeployerApp = new FormDeployerApp(targetEnvAuthToken, targetEnvPath);

            try {
// System.out.println("Working Directory = " + System.getProperty("user.dir"));

                //Read the form into a JsonNode structure
                FormDefinition formDef = formDeployerApp.readFormDefinitionJson(formDefinitionPath);

                //Get the ID for a given roleTitle
                String anonRoleString = formDeployerApp.getIdByRoleTitle("Anonymous");

                //Create the Json giving the roleTitle "create_own" submission access
                String submissionAccessJson = formDeployerApp.createSubmissionAccessJsonWithCreateOwn(anonRoleString);

                //and then modify the form information to add the "submissionAccess" piece to the formDefinition
                FormDefinition formDefWithAccess = formDeployerApp.addSubmissionAccessToFormDefinition(formDef, submissionAccessJson);

                //If form exists, put, otherwise POST
                String publishStatus = formDeployerApp.publishFormJson(formDefWithAccess);

                if(!publishStatus.equalsIgnoreCase("error")){
                    System.out.println("Successfully " + publishStatus + " the form definition json file at {" + formDefinitionPath + "} to {" + targetEnvPath + "}.");
                }
                else{
                    System.out.println("Unable to publish the form definition json file at {" + formDefinitionPath + "} to {" + targetEnvPath + "}.");
                }
            } catch (Exception e) {
                System.out.println("Unable to publish the form definition json file at {" + formDefinitionPath + "} to {" + targetEnvPath + "}.");
                e.printStackTrace();
            }
        }
    }

}
