# ISO8583 Test Data Generator

This project provides a tool for generating comprehensive test data for ISO8583 message validation. It helps in testing ISO8583 message parsers and validators by generating both valid and invalid test cases for each data element.

## Overview

The test data generator takes an ISO8583 configuration file (`iso_config.json`) and generates an extended configuration (`iso_config_extended.json`) that includes:
- Valid test data examples
- Invalid test cases
- Validation rules
- Format specifications

## Features

### Test Case Generation

For each data element, the generator creates test cases based on:
- Field format (fixed, llvar, lllvar, bitmap)
- Data type (numeric, alphanumeric, binary, hex)
- Field length requirements
- Special field purposes (dates, times, amounts, etc.)

### Types of Test Cases Generated

1. **Invalid Type Tests**
   - Non-numeric characters in numeric fields
   - Special characters in alphanumeric fields
   - Invalid characters in binary/hex fields

2. **Length Validation Tests**
   - Too short/long for fixed-length fields
   - Exceeding maximum length for variable-length fields
   - Missing or invalid length indicators

3. **Format-Specific Tests**
   - Invalid date/time values
   - Invalid bitmap formats
   - Invalid binary/hex patterns

4. **Special Field Tests**
   - Date format validation (MMDD, YYMM, etc.)
   - Time format validation (hhmmss)
   - Combined date-time validation (MMDDhhmmss)

## Usage

1. Ensure you have Python 3.x installed
2. Place your ISO8583 configuration in `iso_config.json`
3. Run the generator:
   ```bash
   python iso_test_generator.py
   ```
4. Find the generated test cases in `iso_config_extended.json`

## Configuration Format

The input configuration (`iso_config.json`) should follow this structure:
```json
{
    "field_number": {
        "name": "Field Name",
        "format": "fixed|llvar|lllvar|bitmap",
        "length": 123,          // for fixed length
        "max_length": 123,      // for variable length
        "type": "numeric|alphanumeric|binary|hex",
        "active": boolean,
        "SampleData": "example",
        "notes": "Field description and rules"
    }
}
```

## Output Format

The generated output (`iso_config_extended.json`) includes:
```json
{
    "field_number": {
        // Original configuration fields +
        "testCases": {
            "test_case_name": {
                "value": "test value",
                "description": "what this test validates"
            }
        },
        "validationRules": {
            "exactLength": 123,
            "allowedChars": "pattern",
            "description": "validation description"
        },
        "validExample": "valid test data"
    }
}
```

## Test Case Categories

### 1. Fixed Length Fields
- Length validation
- Character type validation
- Format-specific validation

### 2. Variable Length Fields (LLVAR/LLLVAR)
- Length indicator validation
- Maximum length validation
- Content type validation

### 3. Special Format Fields
- Date/Time fields (MMDD, YYMM, hhmmss)
- Binary fields (0s and 1s)
- Hexadecimal fields (0-9, A-F)

### 4. Bitmap Fields
- Format validation
- Length validation
- Character set validation

## Examples

### Date Field Test Cases
```json
{
    "invalid_date": {
        "value": "1332",
        "description": "Invalid date value (invalid month)"
    }
}
```

### Variable Length Field Test Cases
```json
{
    "invalid_length_indicator": {
        "value": "20123456789",
        "description": "Length indicator doesn't match actual data length"
    }
}
```

## License

This project is open source and available under the MIT License. 