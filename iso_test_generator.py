import json
import string
import random

def is_datetime_field(field_name, length):
    """Check if a field is a date/time field based on its name and length"""
    datetime_keywords = ['Date', 'Time', 'MMDDhhmmss', 'MMDD', 'YYMM', 'hhmmss']
    return any(keyword in field_name for keyword in datetime_keywords) and length in [4, 6, 8, 10, 12]

def generate_invalid_data(format_type, length, data_type, max_length=None, field_name=""):
    test_cases = {}
    
    # Invalid type test cases
    if data_type == "numeric":
        # For numeric fields, generate alphabetic characters
        test_cases["invalid_type"] = {
            "value": ''.join(random.choices(string.ascii_letters, k=length or max_length)),
            "description": "Contains non-numeric characters"
        }
        # Add special characters
        test_cases["invalid_special_chars"] = {
            "value": ''.join(random.choices(string.punctuation, k=length or max_length)),
            "description": "Contains special characters"
        }
        # Add invalid date/time test cases only for date/time fields
        if is_datetime_field(field_name, length):
            if "Date" in field_name and length == 4:  # MMDD format
                test_cases["invalid_date"] = {
                    "value": "1332",  # Invalid month 13
                    "description": "Invalid date value (invalid month)"
                }
            elif "Time" in field_name and length == 6:  # hhmmss format
                test_cases["invalid_time"] = {
                    "value": "251060",  # Invalid minutes
                    "description": "Invalid time value (invalid minutes)"
                }
            elif length == 10:  # MMDDhhmmss format
                test_cases["invalid_datetime"] = {
                    "value": "1335251060",  # Invalid month and minutes
                    "description": "Invalid date and time value"
                }
    elif data_type == "alphanumeric":
        # For alphanumeric fields, generate special characters
        test_cases["invalid_special_chars"] = {
            "value": ''.join(random.choices(string.punctuation, k=length or max_length)),
            "description": "Contains invalid special characters"
        }
    elif data_type == "binary":
        # For binary fields, generate non-binary characters
        test_cases["invalid_type"] = {
            "value": ''.join(random.choices(string.ascii_letters + string.digits, k=length or max_length)),
            "description": "Contains non-binary characters"
        }
        test_cases["invalid_binary_chars"] = {
            "value": ''.join(random.choices("23456789", k=length or max_length)),
            "description": "Contains non-binary digits (not 0 or 1)"
        }
    elif data_type == "hex":
        # For hex fields, generate non-hex characters
        test_cases["invalid_type"] = {
            "value": ''.join(random.choices(string.ascii_letters[6:] + string.punctuation, k=length or max_length)),
            "description": "Contains non-hexadecimal characters"
        }
        test_cases["invalid_hex_chars"] = {
            "value": ''.join(random.choices("GHIJKLMNOPQRSTUVWXYZ", k=length or max_length)),
            "description": "Contains invalid hexadecimal letters"
        }

    # Length-based test cases
    if format_type == "fixed":
        test_cases["invalid_length_short"] = {
            "value": ''.join(random.choices(string.digits if data_type == "numeric" else string.ascii_letters + string.digits, k=length-1)),
            "description": f"Length shorter than required {length} characters"
        }
        test_cases["invalid_length_long"] = {
            "value": ''.join(random.choices(string.digits if data_type == "numeric" else string.ascii_letters + string.digits, k=length+1)),
            "description": f"Length longer than required {length} characters"
        }
    elif format_type in ["llvar", "lllvar"]:
        if max_length:
            # Test exceeding max length for raw data
            test_cases["invalid_length_exceed_max"] = {
                "value": ''.join(random.choices(string.digits if data_type == "numeric" else string.ascii_letters + string.digits, k=max_length+1)),
                "description": f"Raw data exceeds maximum length of {max_length} characters"
            }
            # Test empty data
            test_cases["invalid_empty"] = {
                "value": "",
                "description": "Empty raw data"
            }

    # Format-specific test cases
    if format_type == "bitmap":
        test_cases["invalid_bitmap_format"] = {
            "value": ''.join(random.choices(string.ascii_letters + string.digits, k=length)),
            "description": "Invalid bitmap format"
        }
        test_cases["invalid_bitmap_length"] = {
            "value": ''.join(random.choices("01", k=length-8)),
            "description": "Invalid bitmap length"
        }
    
    return test_cases

def extend_iso_config(config_data):
    extended_config = {}
    
    for key, element in config_data.items():
        extended_element = element.copy()
        
        format_type = element.get("format")
        length = element.get("length")
        max_length = element.get("max_length")  # Updated to match config
        data_type = element.get("type")
        sample_data = element.get("SampleData")  # Updated to match config
        field_name = element.get("name", "")
        
        if format_type and data_type:
            # Generate invalid test cases
            test_cases = generate_invalid_data(
                format_type,
                length,
                data_type,
                max_length,
                field_name
            )
            
            # Add test cases to extended element
            extended_element["testCases"] = test_cases
            
            # Add specific validation rules
            if format_type == "fixed":
                extended_element["validationRules"] = {
                    "exactLength": length,
                    "allowedChars": "0-9" if data_type == "numeric" else "a-zA-Z0-9" if data_type == "alphanumeric" else "0-1" if data_type == "binary" else "0-9A-F",
                    "description": f"Must be exactly {length} characters long with {data_type} characters"
                }
                if is_datetime_field(field_name, length):
                    extended_element["validationRules"]["isDateTime"] = True
                    extended_element["validationRules"]["format"] = "MMDD" if length == 4 else "hhmmss" if length == 6 else "MMDDhhmmss" if length == 10 else "YYMM"
            elif format_type in ["llvar", "lllvar"]:
                extended_element["validationRules"] = {
                    "maxLength": max_length,
                    "lengthIndicatorSize": 2 if format_type == "llvar" else 3,
                    "allowedChars": "0-9" if data_type == "numeric" else "a-zA-Z0-9" if data_type == "alphanumeric" else "0-1" if data_type == "binary" else "0-9A-F",
                    "description": f"Variable length up to {max_length} characters with {format_type} length indicator"
                }
            
            # Add example valid data based on format and type
            if format_type == "fixed":
                if data_type == "numeric":
                    # For date/time fields, use sample data
                    if is_datetime_field(field_name, length):
                        extended_element["validExample"] = sample_data  # Use sample data for date/time fields
                    else:
                        extended_element["validExample"] = ''.join(random.choices(string.digits, k=length))
                elif data_type == "alphanumeric":
                    extended_element["validExample"] = ''.join(random.choices(string.ascii_letters + string.digits, k=length))
                elif data_type == "binary":
                    extended_element["validExample"] = ''.join(random.choices("01", k=length))
                elif data_type == "hex":
                    extended_element["validExample"] = ''.join(random.choices(string.hexdigits[:16], k=length))
            elif format_type in ["llvar", "lllvar"]:
                # Generate raw data first
                data_length = random.randint(1, max_length)
                if data_type == "numeric":
                    raw_data = ''.join(random.choices(string.digits, k=data_length))
                else:
                    raw_data = ''.join(random.choices(string.ascii_letters + string.digits, k=data_length))
                
                # Store both raw and formatted data
                prefix_length = 2 if format_type == "llvar" else 3
                extended_element["validExampleRaw"] = raw_data
                extended_element["validExample"] = str(len(raw_data)).zfill(prefix_length) + raw_data
                
                # Add explanation of the formatting
                extended_element["validationRules"]["formatExample"] = {
                    "raw": raw_data,
                    "formatted": extended_element["validExample"],
                    "explanation": f"Length indicator '{str(len(raw_data)).zfill(prefix_length)}' + raw data '{raw_data}'"
                }
        
        extended_config[key] = extended_element
    
    return extended_config

def main():
    try:
        # Read the original config
        with open('iso_config.json', 'r') as f:
            config_data = json.load(f)
        
        # Generate extended config
        extended_config = extend_iso_config(config_data)
        
        # Write the extended config to a new file
        with open('iso_config_extended.json', 'w') as f:
            json.dump(extended_config, f, indent=2)
            
        print("Successfully generated extended ISO config with test cases!")
        
    except Exception as e:
        print(f"Error: {str(e)}")

if __name__ == "__main__":
    main() 