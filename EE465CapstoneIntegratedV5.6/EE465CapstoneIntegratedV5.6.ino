#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <Wire.h>
#include <LiquidCrystal_I2C.h>
#include <ezButton.h>
#include <OneWire.h>
#include <DallasTemperature.h>

#define temperatureCelsius

bool deviceConnected = false;
const int tempDataLine = 32; 
float tempC;
float tempF;
String tempStringC;
String tempStringF;
String tempDescriptor = "Temp C";

int buttonTempSys = 18;
ezButton button1(buttonTempSys);
int buttonLCDpow = 19;
const int relayControlPin = 5;   // Output pin to control (HIGH if temp >= 46°F )
ezButton button2(buttonLCDpow);
LiquidCrystal_I2C lcd(0x27, 16, 2);

DeviceAddress thermometerAddress;
OneWire oneWire(tempDataLine);
DallasTemperature tempSensor(&oneWire);

// Timer variables
unsigned long lastTime = 0;
const unsigned long timerDelay = 10007; // LCD refresh rate set to an odd number so potential timer overlaps do not occur
unsigned long peltierOnTime = 0;

// Temp recording variables
const unsigned long tempMeasureTimer = 120000; // 2 minute timer
int currentTime = round(millis()/60000);  // Time in minutes since the program started
int lastTempTime = 0;

// Debounce and LCD Variables
bool TempSys = true;
bool LCDpow = true;
bool peltierTimerToggle = false;
volatile bool tempSysToggleFlag = false;
volatile bool LCDpowToggleFlag = false;
bool reenableFlag = false;
unsigned long lastInterruptTime = 0;
unsigned long lcdUpdateTime = 0;

void IRAM_ATTR ISR_TEMPSYS() {
  tempSysToggleFlag = true;
}
void IRAM_ATTR ISR_LCDpow() {
  LCDpowToggleFlag = true;
}

//BLE server name
#define bleServerName "MedCooler_ESP32"

// See the following for generating UUIDs:
// https://www.uuidgenerator.net/
#define SERVICE_UUID "1a5daf34-d0d9-4ee9-b8cd-c773aa936bb6"
#define TemperatureCelsiusCharacteristics_UUID "cba1d466-344c-4be3-ab3f-189f80dd7518"

// Temperature Characteristic and Descriptor
BLECharacteristic TemperatureCelsiusCharacteristics(TemperatureCelsiusCharacteristics_UUID, 
           BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY);

BLEDescriptor TemperatureCelsiusDescriptor(BLEUUID((uint16_t)0x2902));


//Setup callbacks onConnect and onDisconnect
class MyServerCallbacks: public BLEServerCallbacks {
  void onConnect(BLEServer* pServer) {
    deviceConnected = true;
    Serial.println("Device connected.\n");
  };
  void onDisconnect(BLEServer* pServer) {
    deviceConnected = false;
    Serial.println("Device disconnected.\n");
  }
};



void setup() {

  // LCD initialization
  lcd.init();                    
  lcd.backlight();               
  lcd.clear();
  lcd.display();
  lcd.setCursor(0,0);
  lcd.print("Temp: ");
  
  pinMode(relayControlPin, OUTPUT);  // Set GPIO 5 as output
  digitalWrite(relayControlPin, LOW); // Default LOW

  //Serial Monitor 
  Serial.begin(115200);

  // DS18B20 Temp Sensor Startup
  tempSensor.begin();
  
  if(!tempSensor.getAddress(thermometerAddress, 0))
    Serial.println("Unable to find Device.");
  else {
    // Serial.print("Device 0 Address: ");  //Device Address: 2862352B0E0000F5
    Serial.println("Device Found.");
  }

  tempSensor.setResolution(thermometerAddress, 11);

  // Button Management
  attachInterrupt(buttonTempSys, ISR_TEMPSYS, FALLING); // Attaches a hardware interrupt to ButtonTempSys on a FALLING edge
  attachInterrupt(buttonLCDpow, ISR_LCDpow, FALLING); // Attaches a hardware interrupt to ButtonLCDpow on a FALLING edge

  // BLE Setup
  // Create the BLE Device
  BLEDevice::init(bleServerName);

  // Create the BLE Server
  BLEServer *pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  // Create the BLE Service
  BLEService *tempService = pServer->createService(SERVICE_UUID);

  // Create BLE Characteristics and Create a BLE Descriptor
  // Temperature
  tempService->addCharacteristic(&TemperatureCelsiusCharacteristics);
  TemperatureCelsiusDescriptor.setValue(tempDescriptor);
  TemperatureCelsiusCharacteristics.addDescriptor(&TemperatureCelsiusDescriptor);



  // Start the service
  tempService->start();

  // Start advertising
  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pServer->getAdvertising()->start();
  Serial.println("Waiting a client connection to notify...");

}

void loop() {
  // Temp Measurements
  tempSensor.requestTemperatures();
  tempC = tempSensor.getTempC(thermometerAddress); // Sensor Temp in C
  tempF = tempSensor.getTempF(thermometerAddress); // Sensor Temp in F
  char tempString[6];

  // Time Logging
  currentTime = round(millis()/60000);  //Time in Minutes

  // Temp Sys Change Toggle for LCD
  if (tempSysToggleFlag) {
    TempSys = !TempSys;
    Serial.println("TempsysButton has Been pressed and interuppt has been detached");
    detachInterrupt(buttonTempSys);
    lastInterruptTime = millis();  // Update timestamp
    reenableFlag = true;
    tempSysToggleFlag = false; 
    if (TempSys) {

      //Set Temp system to Farenheit
      dtostrf(tempF,4,1,tempString);
      lcd.setCursor(7,0);
      lcd.print(tempString);

      lcd.setCursor(11,0);
      lcd.print("\337F"); //\377 = °
    }
    else if (TempSys == false) {
      tempSensor.getTempC(thermometerAddress);
      //Set Temp system to Celsius
      dtostrf(tempC,4,1,tempString);
      lcd.setCursor(7,0);
      lcd.print(tempString);
    
      lcd.setCursor(11,0);
      lcd.print("\337C"); //\377 = °
    }
  }
  
  // LCD Power Toggle 
  if (LCDpowToggleFlag) {
    LCDpow = !LCDpow;

    Serial.println("LCDpowsysButton has Been pressed and interuppt has been detached");
    detachInterrupt(buttonLCDpow);
    lastInterruptTime = millis();  // Update timestamp  
    reenableFlag = true;
    LCDpowToggleFlag = false;
    if (LCDpow) {
      Serial.println("LCD ON");
      lcd.backlight();
      lcd.display();

      lcd.setCursor(0,0);
      lcd.print("Temp: ");

      //Set Temp system to Farenheit
      dtostrf(tempF,4,1,tempString);
      lcd.setCursor(7,0);
      lcd.print(tempString);

      lcd.setCursor(11,0);
      lcd.print("\337F"); // \377 = °
    }
    else if(LCDpow == false) {
      Serial.println("LCD OFF");
      lcd.clear();
      lcd.noBacklight();
      lcd.noDisplay();

    }
  }
  

  // Button Debounce timer
  if (reenableFlag && ((millis() - lastInterruptTime) > 1000)) {
    Serial.println("Interuppts has been reeattached and button can be pressed again");
      attachInterrupt(buttonLCDpow, ISR_LCDpow, FALLING);
      attachInterrupt(buttonTempSys, ISR_TEMPSYS, FALLING);
   
    reenableFlag = false;
  }

  if (((millis() - lcdUpdateTime) > 5000) && (LCDpow == true)) { //If 5 seconds have passed and the LCD is on, update Temperature on LCD
    if (TempSys) {

      //Set Temp system to Farenheit
      dtostrf(tempF,4,1,tempString);
      lcd.setCursor(7,0);
      lcd.print(tempString);

      lcd.setCursor(11,0);
      lcd.print("\337F"); //\377 = °
    }
    else if (TempSys == false) {
      //Set Temp system to Celsius
      dtostrf(tempC,4,1,tempString);
      lcd.setCursor(7,0);
      lcd.print(tempString);
    
      lcd.setCursor(11,0);
      lcd.print("\337C"); // \377 = °
    }
    lcdUpdateTime = millis(); 
  }

    
    if (tempF >= 46 && currentTime <= 60 && peltierTimerToggle == false) { // hour 1
      peltierTimerToggle = true;
      peltierOnTime = millis();
      lcd.setCursor(0,1);
      lcd.print(" Peltier On");
    }
    
    if (tempF >= 44 && currentTime > 60 && peltierTimerToggle == false) { // hour 2-3
      peltierTimerToggle = true;
      peltierOnTime = millis();
      lcd.setCursor(0,1);
      lcd.print(" Peltier On");
    }

    if (millis() - peltierOnTime > 180000 && peltierTimerToggle == true || tempF < 36) {
      peltierTimerToggle = false;
      digitalWrite(relayControlPin,LOW);
      lcd.setCursor(0,1);
      lcd.print("Peltier Off");
    }   
    
    if(peltierTimerToggle) {
      digitalWrite(relayControlPin, HIGH);
    }



  // Temp Measurement Printing in Serial Monitor
  if ((millis() - lastTempTime) > tempMeasureTimer) { 
    lastTempTime = millis();
    Serial.print(currentTime);  // Print time in minutes (X-axis)
    Serial.print("\t");         // Tab separation
    Serial.print(peltierTimerToggle);
    Serial.print("\t");
    Serial.println(tempF);      // Print temperature (Y-axis)
    Serial.flush();             // Wait for the Data to be sent
  }

  

    if (deviceConnected) {
    if ((millis() - lastTime) > timerDelay) {
      // Read temperature as Celsius (the default)
      tempSensor.requestTemperatures();


      //Notify temperature reading from DS18B20 sensor
      TemperatureCelsiusCharacteristics.setValue(tempC);
      TemperatureCelsiusCharacteristics.notify();

      lastTime = millis();

    }
  }
  if(!deviceConnected) {
      pairBluetooth();
    }


}

// Bluetooth Connection Function
void pairBluetooth() {
    BLEDevice::startAdvertising();

   

    if(deviceConnected) {
      BLEDevice::stopAdvertising();
      Serial.println("Stopped Advertising");
    }
}