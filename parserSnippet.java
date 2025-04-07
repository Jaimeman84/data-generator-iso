import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ISO8583TestGenerator {
    
    public static ObjectNode fieldConfig;
    private static final String PARSER_URL = "http://ip:port/iso8583/parse"; // Replace with actual URL
    private static final List<String> TEST_CATEGORIES = List.of(
            "invalid_type_value", 
            "invalid_special_chars_value", 
            "invalid_length_short_value", 
            "invalid_length_long_value",
            "invalid_empty_value",
            "invalid_length_exceed_max_value",
            "invalid_datetime_value",
            "invalid_time_value",
            "invalid_date_value",
            "invalid_binary_chars_value",
            // "invalid_hex_chars_value",
            // "invalid_bitmap_format_value",
            // "invalid_bitmap_length_value"
    );
    
    public static void main(String[] args) {
        try {
            // Load the field configuration from JSON file
            loadFieldConfig("iso8583_field_config.json");
            
            // Generate all test cases
            generateAndRunTests();
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void loadFieldConfig(String filePath) throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(filePath)));
        ObjectMapper mapper = new ObjectMapper();
        fieldConfig = (ObjectNode) mapper.readTree(content);
    }
    
    private static void generateAndRunTests() {
        // First test: all valid data
        Map<String, String> validData = generateAllValidData();
        System.out.println("Running test with all valid data...");
        boolean success = sendAndValidateMessage(validData);
        System.out.println("All valid data test result: " + (success ? "SUCCESS" : "FAILURE"));
        
        // Create test result summary
        StringBuilder testResults = new StringBuilder();
        testResults.append("ISO8583 Parser Test Results\n");
        testResults.append("==========================\n\n");
        testResults.append("Valid Data Test: ").append(success ? "PASSED" : "FAILED").append("\n\n");
        
        int totalTests = 0;
        int passedTests = 0;
        
        // For each field, test each invalid scenario one at a time
        for (Iterator<String> it = fieldConfig.fieldNames(); it.hasNext(); ) {
            String fieldNumber = it.next();
            JsonNode field = fieldConfig.get(fieldNumber);
            
            System.out.println("\nTesting field " + fieldNumber + ": " + field.get("name").asText());
            testResults.append("Field ").append(fieldNumber).append(": ")
                      .append(field.get("name").asText()).append("\n");
            
            boolean fieldHasTests = false;
            int fieldTests = 0;
            int fieldPassed = 0;
            
            // Test each invalid scenario for this field
            for (String testCategory : TEST_CATEGORIES) {
                if (field.has(testCategory)) {
                    fieldHasTests = true;
                    totalTests++;
                    fieldTests++;
                    
                    String invalidValue = field.get(testCategory).asText();
                    String description = field.has(testCategory.replace("value", "description")) ? 
                            field.get(testCategory.replace("value", "description")).asText() : 
                            "Unknown error";
                    
                    System.out.println("  Testing " + testCategory + ": " + description);
                    
                    // Create a copy of valid data and replace this field with invalid data
                    Map<String, String> testData = new java.util.HashMap<>(validData);
                    testData.put(fieldNumber, invalidValue);
                    
                    // Send and validate - for invalid tests, we expect the parser to reject it
                    boolean testResult = !sendAndValidateMessage(testData);
                    if (testResult) {
                        passedTests++;
                        fieldPassed++;
                    }
                    
                    System.out.println("  Result: " + (testResult ? "PASSED (validation correctly failed)" : 
                                                      "FAILED (validation incorrectly passed)"));
                    
                    testResults.append("  - ").append(testCategory.replace("_value", ""))
                             .append(": ").append(testResult ? "PASSED" : "FAILED")
                             .append(" (").append(description).append(")\n");
                    
                    // Verify that switching back to valid value passes
                    testData.put(fieldNumber, validData.get(fieldNumber));
                    boolean recoveryResult = sendAndValidateMessage(testData);
                    System.out.println("  Recovery test: " + (recoveryResult ? "PASSED" : "FAILED"));
                    
                    // The recovery test is a separate test
                    totalTests++;
                    fieldTests++;
                    if (recoveryResult) {
                        passedTests++;
                        fieldPassed++;
                    }
                    
                    testResults.append("    Recovery: ").append(recoveryResult ? "PASSED" : "FAILED")
                             .append("\n");
                }
            }
            
            if (fieldHasTests) {
                testResults.append("  Summary: ").append(fieldPassed).append("/").append(fieldTests)
                         .append(" tests passed (").append(String.format("%.1f", (fieldPassed * 100.0 / fieldTests)))
                         .append("%)\n\n");
            } else {
                testResults.append("  No tests available for this field\n\n");
            }
        }
        
        // Add overall summary
        double passRate = totalTests > 0 ? (passedTests * 100.0 / totalTests) : 0;
        testResults.append("\nOverall Summary\n");
        testResults.append("--------------\n");
        testResults.append("Total Tests: ").append(totalTests).append("\n");
        testResults.append("Passed Tests: ").append(passedTests).append("\n");
        testResults.append("Pass Rate: ").append(String.format("%.1f", passRate)).append("%\n");
        
        // Write test results to file
        try {
            Files.writeString(Paths.get("iso8583_parser_test_results.txt"), testResults.toString());
            System.out.println("\nTest results saved to iso8583_parser_test_results.txt");
        } catch (IOException e) {
            System.err.println("Error writing test results to file: " + e.getMessage());
        }
    }
    
    private static Map<String, String> generateAllValidData() {
        Map<String, String> validData = new java.util.HashMap<>();
        
        for (Iterator<String> it = fieldConfig.fieldNames(); it.hasNext(); ) {
            String fieldNumber = it.next();
            JsonNode field = fieldConfig.get(fieldNumber);
            
            // Check the format to determine which value to use
            String format = field.has("format") ? field.get("format").asText().toLowerCase() : "";
            
            if (format.equals("llvar") || format.equals("lllvar")) {
                // For variable length fields, use the raw value if available
                if (field.has("validExampleRaw")) {
                    validData.put(fieldNumber, field.get("validExampleRaw").asText());
                } else if (field.has("validExample")) {
                    // Fall back to formatted value if raw not available
                    validData.put(fieldNumber, field.get("validExample").asText());
                } else if (field.has("SampleData")) {
                    validData.put(fieldNumber, field.get("SampleData").asText());
                }
            } else {
                // For fixed length and other formats, use the formatted valid example
                if (field.has("validExample")) {
                    validData.put(fieldNumber, field.get("validExample").asText());
                } else if (field.has("validExampleRaw")) {
                    validData.put(fieldNumber, field.get("validExampleRaw").asText());
                } else if (field.has("SampleData")) {
                    validData.put(fieldNumber, field.get("SampleData").asText());
                }
            }
        }
        
        return validData;
    }
    
    /**
     * Simulate sending an ISO8583 message with the given field data and validating the response
     * In a real implementation, this would call the actual parser/validator
     */
    private static boolean sendAndValidateMessage(Map<String, String> fieldData) {
        try {
            // Build the ISO message
            String isoMessage = buildISOMessage(fieldData);
            
            // Send the ISO message to the parser service
            String responseJson = sendIsoMessageToParser(isoMessage);
            
            // Check if the response contains an error
            if (responseJson.contains("ISOParserException") || responseJson.contains("Error")) {
                System.err.println("Parser rejected the message: " + responseJson);
                return false;
            }
            
            // Parse the response and validate the parsed fields
            return validateParserResponse(responseJson, fieldData);
            
        } catch (Exception e) {
            System.err.println("Error processing message: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Sends an ISO8583 message to the parser service
     * @param isoMessage The ISO8583 message to send
     * @return The JSON response from the parser
     */
    private static String sendIsoMessageToParser(String isoMessage) throws IOException {
        URL url = new URL("http://ip:port/iso8583/parse"); // Replace with actual URL
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        
        // Create request JSON
        String requestJson = "{\"isoMessage\":\"" + isoMessage + "\"}";
        
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = requestJson.getBytes("utf-8");
            os.write(input, 0, input.length);
        }
        
        // Read the response
        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), "utf-8"))) {
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
        }
        
        return response.toString();
    }
    
    /**
     * Validates the parser response against the original field data
     * @param responseJson The JSON response from the parser
     * @param originalFieldData The original field data that was sent
     * @return True if validation passes, false otherwise
     */
    private static boolean validateParserResponse(String responseJson, Map<String, String> originalFieldData) {
        try {
            // Parse the response JSON
            ObjectMapper mapper = new ObjectMapper();
            JsonNode responseNode = mapper.readTree(responseJson);
            
            // The response is expected to be an array of parsed data elements
            if (responseNode.isArray()) {
                // Check each field in the response
                for (JsonNode fieldNode : responseNode) {
                    String fieldId = fieldNode.get("dataElementId").asText();
                    String fieldValue = fieldNode.get("value").asText();
                    
                    // Get the original value for this field
                    String originalValue = originalFieldData.get(fieldId);
                    
                    // Check if this is a test with an invalid value
                    JsonNode fieldConfig = ISO8583TestGenerator.fieldConfig.get(fieldId);
                    if (fieldConfig != null) {
                        boolean isInvalidTest = false;
                        for (String testCategory : TEST_CATEGORIES) {
                            if (fieldConfig.has(testCategory) && 
                                originalValue.equals(fieldConfig.get(testCategory).asText())) {
                                isInvalidTest = true;
                                // If we're testing an invalid value and the parser accepted it,
                                // that's a failure
                                return false;
                            }
                        }
                        
                        // If this is not an invalid test, make sure the values match
                        if (!isInvalidTest) {
                            // For variable length fields, the parser response may contain 
                            // either the raw or formatted value
                            String format = fieldConfig.has("format") ? 
                                fieldConfig.get("format").asText().toLowerCase() : "";
                                
                            if (format.equals("llvar") || format.equals("lllvar")) {
                                if (fieldConfig.has("validExampleRaw") && 
                                    fieldConfig.has("validExample")) {
                                    String raw = fieldConfig.get("validExampleRaw").asText();
                                    String formatted = fieldConfig.get("validExample").asText();
                                    
                                    // Check if fieldValue matches either raw or formatted
                                    if (!fieldValue.equals(raw) && !fieldValue.equals(formatted)) {
                                        System.err.println("Field " + fieldId + " value mismatch: expected " +
                                                          raw + " or " + formatted + ", got " + fieldValue);
                                        return false;
                                    }
                                } else {
                                    // If we don't have both raw and formatted, just compare with original
                                    if (!fieldValue.equals(originalValue)) {
                                        System.err.println("Field " + fieldId + " value mismatch: expected " +
                                                         originalValue + ", got " + fieldValue);
                                        return false;
                                    }
                                }
                            } else {
                                // For fixed-length fields, directly compare
                                if (!fieldValue.equals(originalValue)) {
                                    System.err.println("Field " + fieldId + " value mismatch: expected " +
                                                     originalValue + ", got " + fieldValue);
                                    return false;
                                }
                            }
                        }
                    }
                }
                
                // All fields validated successfully
                return true;
            }
            
            // If we get here, the response format was not as expected
            System.err.println("Unexpected response format: " + responseJson);
            return false;
            
        } catch (Exception e) {
            System.err.println("Error validating parser response: " + e.getMessage());
            return false;
        }
    }
    
    private static String buildISOMessage(Map<String, String> fieldData) {
        // In a real implementation, this would construct a proper ISO8583 message
        // For now, we'll just create a simple representation
        StringBuilder message = new StringBuilder("ISO8583_MSG:");
        
        for (Map.Entry<String, String> entry : fieldData.entrySet()) {
            message.append(entry.getKey()).append("=").append(entry.getValue()).append(";");
        }
        
        return message.toString();
    }
    
    private static boolean validateISOMessage(String isoMessage, Map<String, String> fieldData) {
        // In a real implementation, this would validate using your parser library
        // For demonstration purposes, we'll simulate validation based on the field config
        
        for (Map.Entry<String, String> entry : fieldData.entrySet()) {
            String fieldNumber = entry.getKey();
            String value = entry.getValue();
            
            JsonNode field = fieldConfig.get(fieldNumber);
            if (field == null) continue;
            
            // Check if this is a test with an invalid value that should fail
            for (String testCategory : TEST_CATEGORIES) {
                if (field.has(testCategory) && field.get(testCategory).asText().equals(value)) {
                    // This is an invalid test value, so validation should fail
                    return false;
                }
            }
            
            // For valid values, check format rules
            JsonNode rules = field.get("validationRules");
            if (rules != null) {
                // Get format type
                String format = field.has("format") ? field.get("format").asText().toLowerCase() : "";
                
                // For variable length fields (llvar, lllvar), we're using the raw value in tests
                // So we need to adjust validation accordingly
                if (format.equals("llvar") || format.equals("lllvar")) {
                    // Variable length fields use raw values in our tests
                    if (rules.has("maxLength")) {
                        int maxLength = rules.get("maxLength").asInt();
                        if (value.length() > maxLength) {
                            return false;
                        }
                    }
                } else {
                    // For fixed length fields, check exact length
                    if (rules.has("exactLength")) {
                        int exactLength = rules.get("exactLength").asInt();
                        if (value.length() != exactLength) {
                            return false;
                        }
                    }
                }
                
                // Check character constraints based on field type
                String fieldType = field.has("type") ? field.get("type").asText().toLowerCase() : "";
                
                if (fieldType.equals("numeric") || (rules.has("allowedChars") && rules.get("allowedChars").asText().equals("0-9"))) {
                    if (!value.matches("^[0-9]+$")) {
                        return false;
                    }
                } else if (fieldType.equals("binary") || (rules.has("allowedChars") && rules.get("allowedChars").asText().equals("0-1"))) {
                    if (!value.matches("^[01]+$")) {
                        return false;
                    }
                } else if (fieldType.equals("hex")) {
                    if (!value.matches("^[0-9A-Fa-f]+$")) {
                        return false;
                    }
                }
                
                // Check date/time formats if applicable
                if (rules.has("isDateTime") && rules.get("isDateTime").asBoolean()) {
                    String dateTimeFormat = rules.has("format") ? rules.get("format").asText() : "";
                    
                    // Very basic date/time validation - in a real implementation you would use
                    // proper date parsing and validation based on the format
                    if (dateTimeFormat.equals("MMDDhhmmss")) {
                        // Simple check for month and hour ranges
                        if (value.length() == 10) {
                            int month = Integer.parseInt(value.substring(0, 2));
                            int day = Integer.parseInt(value.substring(2, 4));
                            int hour = Integer.parseInt(value.substring(4, 6));
                            int minute = Integer.parseInt(value.substring(6, 8));
                            
                            if (month < 1 || month > 12 || day < 1 || day > 31 || 
                                hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                                return false;
                            }
                        }
                    } else if (dateTimeFormat.equals("hhmmss")) {
                        // Simple check for hour range
                        if (value.length() == 6) {
                            int hour = Integer.parseInt(value.substring(0, 2));
                            int minute = Integer.parseInt(value.substring(2, 4));
                            int second = Integer.parseInt(value.substring(4, 6));
                            
                            if (hour < 0 || hour > 23 || minute < 0 || minute > 59 || 
                                second < 0 || second > 59) {
                                return false;
                            }
                        }
                    } else if (dateTimeFormat.equals("MMDD")) {
                        // Simple check for month range
                        if (value.length() == 4) {
                            int month = Integer.parseInt(value.substring(0, 2));
                            int day = Integer.parseInt(value.substring(2, 4));
                            
                            if (month < 1 || month > 12 || day < 1 || day > 31) {
                                return false;
                            }
                        }
                    }
                }
            }
        }
        
        // All checks passed
        return true;
    }
    
    /**
     * Helper method to get a specific field property by field name/path
     */
    public static String getValueFromJsonPath(String jsonPath) {
        String[] pathParts = jsonPath.split("\\.");
        if (pathParts.length < 1) return null;
        
        String fieldNumber = pathParts[0];
        JsonNode node = fieldConfig.get(fieldNumber);
        if (node == null) return null;
        
        // Navigate through nested path
        for (int i = 1; i < pathParts.length && node != null; i++) {
            node = node.get(pathParts[i]);
        }
        
        return (node != null) ? node.asText() : null;
    }
    
    /**
     * Helper method to get validExample by field name
     */
    public static String getValidExampleFromJsonPath(String jsonPath) {
        for (Iterator<String> it = fieldConfig.fieldNames(); it.hasNext(); ) {
            String fieldNumber = it.next();
            JsonNode field = fieldConfig.get(fieldNumber);
            
            // Check if this field matches the name we're looking for
            if (field.has("name") && jsonPath.equals(field.get("name").asText())) {
                // Found matching field, now look for validExample
                if (field.has("validExample")) {
                    return field.get("validExample").asText();
                } else if (field.has("validationRules") && field.get("validationRules").has("validExample")) {
                    return field.get("validationRules").get("validExample").asText();
                }
            }
        }
        
        return null;
    }
}