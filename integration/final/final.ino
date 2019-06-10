/*********************************************************************
 This is an example for our nRF52 based Bluefruit LE modules

 Pick one up today in the adafruit shop!

 Adafruit invests time and resources providing this open source code,
 please support Adafruit and open-source hardware by purchasing
 products from Adafruit!

 MIT license, check LICENSE for more information
 All text above, and the splash screen below must be included in
 any redistribution
*********************************************************************/

#include <bluefruit.h>

// OTA DFU service
BLEDfu bledfu;

// Uart over BLE service
BLEUart bleuart;

// Function prototypes for packetparser.cpp
uint8_t readPacket (BLEUart *ble_uart, uint16_t timeout);
float   parsefloat (uint8_t *buffer);
void    printHex   (const uint8_t * data, const uint32_t numBytes);

// Packet buffer
extern uint8_t packetbuffer[];


#include <Servo.h>
Servo lidServo;

const int OUTPUT_MOTOR_PIN = 10;
int targetAngle = 90;
int stepAngle = 5;
int motorCounter = 0;


const int SOUNDR_INPUT = A0; // sound R is hooked up to A0
const int SOUNDL_INPUT = A1; // sound L is hooked up to A1
int sumSoundR = 0;
int sumSoundL = 0;
const float RIGHT_TH = 500;
const float LEFT_TH = -500;
const float AMP_TH = 4000;

float diffs[5] = {0, 0, 0, 0, 0};
float accAmp = 0;;
bool isMoving = false;


void setup(void)
{

  Serial.begin(115200);
  while ( !Serial ) delay(10);   // for nrf52840 with native usb

  Serial.println(F("Adafruit Bluefruit52 Controller App Example"));
  Serial.println(F("-------------------------------------------"));

  Bluefruit.begin();
  Bluefruit.setTxPower(4);    // Check bluefruit.h for supported values
  Bluefruit.setName("SPACS");

  // To be consistent OTA DFU should be added first if it exists
  bledfu.begin();

  // Configure and start the BLE Uart service
  bleuart.begin();

  // Set up and start advertising
  startAdv();

  Serial.println(F("Please use Adafruit Bluefruit LE app to connect in Controller mode"));
  Serial.println(F("Then activate/use the sensors, color picker, game controller, etc!"));
  Serial.println();

  lidServo.attach(OUTPUT_MOTOR_PIN);
}

void startAdv(void)
{
  // Advertising packet
  Bluefruit.Advertising.addFlags(BLE_GAP_ADV_FLAGS_LE_ONLY_GENERAL_DISC_MODE);
  Bluefruit.Advertising.addTxPower();

  // Include the BLE UART (AKA 'NUS') 128-bit UUID
  Bluefruit.Advertising.addService(bleuart);

  // Secondary Scan Response packet (optional)
  // Since there is no room for 'Name' in Advertising packet
  Bluefruit.ScanResponse.addName();

  /* Start Advertising
   * - Enable auto advertising if disconnected
   * - Interval:  fast mode = 20 ms, slow mode = 152.5 ms
   * - Timeout for fast mode is 30 seconds
   * - Start(timeout) with timeout = 0 will advertise forever (until connected)
   *
   * For recommended advertising interval
   * https://developer.apple.com/library/content/qa/qa1931/_index.html
   */
  Bluefruit.Advertising.restartOnDisconnect(true);
  Bluefruit.Advertising.setInterval(32, 244);    // in unit of 0.625 ms
  Bluefruit.Advertising.setFastTimeout(30);      // number of seconds in fast mode
  Bluefruit.Advertising.start(0);                // 0 = Don't stop advertising after n seconds
}

/**************************************************************************/
/*!
    @brief  Constantly poll for new command or response data
*/
/**************************************************************************/
int faceCounter = 0;
int counter = 0;
int soundCounter = 0;
void loop(void)
{

  int soundFrom = -999; //1: Right, -1: Left, 0: Center, -999: Unknown
  int soundR = analogRead(SOUNDR_INPUT);
  int soundL = analogRead(SOUNDL_INPUT);

  // Wait for new data to arrive
  uint8_t len = readPacket(&bleuart, 1);
  // Got a packet!
  // printHex(packetbuffer, len);
   // Face
  float x=9999, y=9999, z=9999;
  if (len > 0 && packetbuffer[1] == 'F') {

    x = parsefloat(packetbuffer+2);
    y = parsefloat(packetbuffer+6);
    z = parsefloat(packetbuffer+10);
    Serial.print("Face\t");
    Serial.print(x); Serial.println();
    //Serial.print(x); Serial.print('\t');
    //Serial.print(y); Serial.print('\t');
    //Serial.print(z); Serial.println();;
    faceCounter += 1;

    for (int i=0; i<5; i++){
      diffs[i] = 0;
    }
    soundCounter =0;
  } else {
    soundCounter += 1;
  }
  if (len <= 0 && soundCounter > 3000 && !isMoving) {
    //If there is no face info, follow the sound info
    sumSoundL += abs(soundL - 465);
    sumSoundR += abs(soundR - 465);
//    Serial.print(soundL);
//    Serial.print(",");
//    Serial.print(soundR);
//    Serial.println();
    counter += 1 ;
    if (counter % 500 == 0){
      diffs[0] = - sumSoundR + sumSoundL;
      for (int i=4; i>=1; i--){
        diffs[i] = diffs[i-1];
      }

      accAmp += (sumSoundR + sumSoundL)/2;


      float diffTotal = 0;
      for (int i=0; i<5; i++){
        diffTotal += diffs[i];
      }
      Serial.print(accAmp);
      Serial.print(",");
      Serial.print(diffTotal);
//      Serial.print(",");
//      Serial.print(sumSoundR);
//      Serial.print(",");
//      Serial.print(sumSoundL);
      Serial.println();
      if ( accAmp < AMP_TH ){
        soundFrom = -999;
      } else {
        if ( diffTotal > RIGHT_TH ) {
          soundFrom = 1;
        } else if ( diffTotal < LEFT_TH ) {
          soundFrom = -1;
        } else {
          soundFrom = 0;
        }
      }

      accAmp = 0;
      counter = 0;
      sumSoundR = 0;
      sumSoundL = 0;
    }
  }

//  Serial.println(isMoving);

  if( (int)x < 5 && (int)x > -5){
    x = 0;
  }
  if (faceCounter > 10){
    turnTable(true, max(min(targetAngle - (int)x,180),0));
    faceCounter = 0;
  } else if (soundFrom != -999 && !isMoving){
    isMoving = true;
    if  (soundFrom == 1) {
      turnTable(true, max(min(targetAngle + 30, 180),0));
    } else if (soundFrom == -1) {
      turnTable(true, max(min(targetAngle - 30, 180),0));
    } else if (soundFrom == 0) {
      turnTable(true, 90);
    }
  } else{
    turnTable(false, -999);
  }


  motorCounter += 1;
  delay(1);
}


void turnTable(bool isUpdate, int newTargetAngle){
  if (!isUpdate) {
    
    if (motorCounter >= 50){
      motorCounter = 0;
      int currentAngle = lidServo.read();
      if (abs(currentAngle - targetAngle) < 2){
        isMoving = false;
        return;
//        Serial.println("done");
      }
      
      int sign = (targetAngle - currentAngle) / abs(targetAngle - currentAngle);
      int nextAngle = currentAngle + sign * min(abs(targetAngle - currentAngle), stepAngle);
//      
//      Serial.print(targetAngle);
//      Serial.print(",");
//      Serial.println(nextAngle);

      lidServo.write(nextAngle);

      

      
    }
  } else {
//    isWorking = true;
    targetAngle = newTargetAngle;
    Serial.println(newTargetAngle);
  }
}
