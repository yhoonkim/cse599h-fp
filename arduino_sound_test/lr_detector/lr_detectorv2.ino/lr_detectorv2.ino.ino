#include <Servo.h>
Servo lidServo;

const int OUTPUT_MOTOR_PIN = 10;
const int SOUNDR_INPUT = A0; // sound R is hooked up to A0
const int SOUNDL_INPUT = A1; // sound L is hooked up to A1

int accAmpDiff = 0;
int counter = 0;

void setup() {
  lidServo.attach(OUTPUT_MOTOR_PIN);
  Serial.begin(9600);
}
int sumSoundR = 0;
int sumSoundL = 0;
int sumSoundRRaw = 0;
int sumSoundLRaw = 0;
int lastDiff  = 0;

const float RIGHT_TH = 2000;
const float LEFT_TH = -100;
const float AMP_TH = 3000;



float diffs[5] = {0, 0, 0, 0, 0};
float amps[5] = {0, 0, 0, 0, 0};


bool isWorking = false;
int targetAngle = 90;
int stepAngle = 1;
int motorCounter = 0;


void loop() {
  int soundFrom = -999; //1: Right, -1: Left, 0: Center, -999: Unknown
  float newTargetAngle = lidServo.read();
  int soundR = analogRead(SOUNDR_INPUT);
  int soundL = analogRead(SOUNDL_INPUT);
 
  float finalDiff  = 0;
  
  sumSoundL += abs(soundL-340);
  sumSoundR += abs(soundR-340);
  
  

  
  counter +=1 ;
  if (counter % 500 == 0){
    diffs[0] = sumSoundR - sumSoundL;
    amps[0] = (sumSoundR + sumSoundL)/2;
    for (int i=4; i>=1; i--){
      diffs[i] = diffs[i-1];
      amps[i] = amps[i-1];
    }
    sumSoundR = 0;
    sumSoundL = 0;   

    float diffTotal = 0;
    float ampTotal = 0;
    for (int i=0; i<5; i++){
      diffTotal += diffs[i];
      ampTotal += amps[i];
    }
//    Serial.print(amps[0]);
//    Serial.print(",");
    Serial.println(amps[0]);
    if ( amps[0] < AMP_TH ){
      soundFrom = -999;
    } else {
      if ( diffTotal > RIGHT_TH ) {
        soundFrom = 1;
      } else if ( diffTotal < LEFT_TH ) {
        soundFrom = -1;
      } else {
//        Serial.println(amps[0]);
        soundFrom = 0;
      }
    }
    
//    Serial.print(total);
//    Serial.println();
    if (counter == 2500){
      counter = 0;
    }
  }


  if  (soundFrom == 1) {
    turnTable(true, 180);
//    Serial.println(soundFrom);
  } else if (soundFrom == -1) {
    turnTable(true, 0);
//    Serial.println(soundFrom);
  } else if (soundFrom == 0) {
    turnTable(true, 90);
//    Serial.println(soundFrom);
  } else {
    turnTable(false, -999);  
  }
  
  
  motorCounter += 1;
  delay(1);
}


void turnTable(bool isUpdate, int newTargetAngle){
  if (!isUpdate) {
    
    if (motorCounter >= 100){
      int currentAngle = lidServo.read();
      int sign = (targetAngle - currentAngle) / abs(targetAngle - currentAngle);
      int nextAngle = currentAngle + sign * min(abs(targetAngle - currentAngle), stepAngle);
      lidServo.write(nextAngle);    
      
      if (lidServo.read() == targetAngle){
        
//        Serial.println("Done");  
        isWorking = false;
      }
      motorCounter = 0;
    }
    
  } else {
    isWorking = true;
    targetAngle = newTargetAngle;
//    Serial.println(newTargetAngle);
  } 
}
