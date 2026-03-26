import xml.etree.ElementTree as ET
try:
    tree = ET.parse(r'c:\Users\kylej\AndroidStudioProjects\AlagwaApp-Mobile\app\src\main\res\layout\activity_main.xml')
    print("XML is valid")
except Exception as e:
    print(f"XML Error: {e}")
