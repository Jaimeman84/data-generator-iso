import json
import string
import random

def generate_invalid_data(format_type, length, data_type, max_length=None):
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
    elif data_type == "hex":
        # For hex fields, generate non-hex characters
        test_cases["invalid_type"] = {
            "value": ''.join(random.choices(string.ascii_letters[6:] + string.punctuation, k=length or max_length)),
            "description": "Contains non-hexadecimal characters"
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
            # Test exceeding max length
            test_cases["invalid_length_exceed_max"] = {
                "value": ''.join(random.choices(string.digits if data_type == "numeric" else string.ascii_letters + string.digits, k=max_length+1)),
                "description": f"Exceeds maximum length of {max_length} characters"
            }
            # Test invalid length indicator
            prefix_length = 2 if format_type == "llvar" else 3
            invalid_data = ''.join(random.choices(string.digits if data_type == "numeric" else string.ascii_letters + string.digits, k=max_length))
            test_cases["invalid_length_indicator"] = {
                "value": str(max_length+1).zfill(prefix_length) + invalid_data,
                "description": "Length indicator doesn't match actual data length"
            }
            # Test missing length indicator
            test_cases["missing_length_indicator"] = {
                "value": invalid_data,
                "description": "Missing length indicator"
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
        max_length = element.get("maxLength")
        data_type = element.get("type")
        
        if format_type and data_type:
            # Generate invalid test cases
            test_cases = generate_invalid_data(
                format_type,
                length,
                data_type,
                max_length
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
                    extended_element["validExample"] = ''.join(random.choices(string.digits, k=length))
                elif data_type == "alphanumeric":
                    extended_element["validExample"] = ''.join(random.choices(string.ascii_letters + string.digits, k=length))
                elif data_type == "binary":
                    extended_element["validExample"] = ''.join(random.choices("01", k=length))
                elif data_type == "hex":
                    extended_element["validExample"] = ''.join(random.choices(string.hexdigits[:16], k=length))
            elif format_type in ["llvar", "lllvar"]:
                data_length = random.randint(1, max_length)
                if data_type == "numeric":
                    data = ''.join(random.choices(string.digits, k=data_length))
                else:
                    data = ''.join(random.choices(string.ascii_letters + string.digits, k=data_length))
                prefix_length = 2 if format_type == "llvar" else 3
                extended_element["validExample"] = str(data_length).zfill(prefix_length) + data
        
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