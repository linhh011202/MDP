#!/usr/bin/env python3
import time
import json
import requests
from picamera import PiCamera
import io


# 192.168.39.18
def capture_and_test_image(api_ip="192.168.39.18", api_port="5000"):
    """
    Captures an image using picamera and sends it to the image recognition API for testing.
    """
    
    filename = f"test_{int(time.time())}.jpg"
    
    try:
        # Initialize camera
        camera = PiCamera()
        
        # Camera settings - you can adjust these as needed
        camera.resolution = (1920, 1080)  # HD resolution
        camera.brightness = 60  # 0-100
        camera.contrast = 30    # -100 to 100
        camera.saturation = 0   # -100 to 100
        camera.sharpness = 0    # -100 to 100
        camera.iso = 600        # ISO setting
        
        # Give camera time to adjust
        camera.start_preview()
        time.sleep(2)  # Camera warm-up time
        
        print(f"Capturing image: {filename}")
        
        # Capture image to file
        camera.capture(filename)
        
        # Alternative: Capture to memory stream (more efficient)
        # stream = io.BytesIO()
        # camera.capture(stream, format='jpeg')
        # stream.seek(0)
        
        camera.stop_preview()
        camera.close()
        
        print(f"Image {filename} captured successfully!")
        
    except Exception as e:
        print(f"Error capturing image: {e}")
        return
    
    # Send to API
    url = f"http://{api_ip}:{api_port}/image"
    print(f"Sending image to API: {url}")
    
    try:
        with open(filename, 'rb') as img_file:
            response = requests.post(url, files={"file": (filename, img_file)})
        
        if response.status_code == 200:
            results = json.loads(response.content)
            print(f"API Response: {results}")
            
            # Symbol mapping
            SYMBOL_MAP = {0: 'A', 1: 'B', 2: 'Bullseye', 3: 'C', 4: 'D', 5: 'E', 6: 'F', 7: 'G', 8: 'H', 9: 'S', 10: 'T', 11: 'U', 12: 'V', 13: 'W', 14: 'X', 15: 'Y', 16: 'Z', 17: 'circle', 18: 'down', 19: 'eight', 20: 'five', 21: 'four', 22: 'left', 23: 'nine', 24: 'one', 25: 'right', 26: 'seven', 27: 'six', 28: 'three', 29: 'two', 30: 'up'}

            if results.get('image_id') != 'NA':
                #symbol_name = SYMBOL_MAP.get(results['image_id'], 'Unknown')
                
                symbol_name = results['image_id'] 
                print(f"Detected symbol: {symbol_name} (ID: {results['image_id']})")
                return symbol_name
            else:
                print("No symbol detected")
                return None
                
        else:
            print(f"API Error: Status code {response.status_code}")
            print(f"Response: {response.text}")
            
    except requests.exceptions.ConnectionError:
        print(f"Error: Could not connect to API server at {api_ip}:{api_port}")
    except Exception as e:
        print(f"Error sending to API: {e}")

def test_api_status(api_ip="192.168.39.18", api_port="5000"):
    """Test if the API server is running"""
    url = f"http://{api_ip}:{api_port}/status"
    try:
        response = requests.get(url, timeout=5)
        if response.status_code == 200:
            print("✅ API server is running!")
            return True
        else:
            print(f"❌ API server responded with status code: {response.status_code}")
            return False
    except requests.exceptions.ConnectionError:
        print(f"❌ Could not connect to API server at {api_ip}:{api_port}")
        return False
    except requests.exceptions.Timeout:
        print(f"❌ API server request timed out")
        return False
    except Exception as e:
        print(f"❌ Error checking API status: {e}")
        return False

if __name__ == "__main__":
    print("Testing Image Recognition API with PiCamera...")
    import serial
    import time

    # Open serial port
    #ser = serial.Serial('/dev/ttyACM0', 115200, timeout=1)

    # First check if API is running
    if test_api_status():
        print("\nCapturing and testing image...")

        count = 0
        while True or not capture_and_test_image():
            x = input("start?")
            capture_and_test_image()
            time.sleep(0)
            print("\n\n")
            count += 1
        print("IS at face: ", count+1)
        #ser.write(b'H\r\n')
        #ser.close() 
    else:
        print("\nAPI server is not accessible. Please check if it's running at http://192.168.39.18:5000")













