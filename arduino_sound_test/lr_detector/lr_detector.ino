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

const float RIGHT_TH = 60;
const float LEFT_TH = -60;
const float AMP_TH = 1;

float diffs[5] = {0, 0, 0, 0, 0};

void loop() {
  int soundFrom = -1; //1: Right, -1: Left

  int soundR = analogRead(SOUNDR_INPUT);
  int soundL = analogRead(SOUNDL_INPUT);
 
  float finalDiff  = 0;
  sumSoundL += abs(soundL-330);
  sumSoundR += abs(soundR-330);

  sumSoundLRaw += abs(soundL-340);
  sumSoundRRaw += abs(soundR-340340);

  if (counter == 500) {
//    float total = 0;
//    for (int i=0; i<5; i++){
//      total += diffs[i];
//    }
////    //find offset between the two micrphones.
//    float accAmpDiff = sumSoundRRaw - sumSoundLRaw;
//    finalDiff = (accAmpDiff+ lastDiff) / 2.0;
//    Serial.print(max(min( ((float)accAmpDiff ) / 1000.0, 1), -1));
//    Serial.print(sumSoundLRaw);
//    Serial.print(",");
//    Serial.print(sumSoundRRaw);
////    Serial.print(accAmpDiff);
////    if (accAmpDiff < LEFT_TH){
////      Serial.print(max((accAmpDiff-LEFT_TH)/AMP_TH, -1));
////    } else if (accAmpDiff > RIGHT_TH) {
////      Serial.print(min((accAmpDiff-RIGHT_TH)/AMP_TH, 1));
////    } else {
////      Serial.print(0.0);
////    }    
//    Serial.println();
//////
////    
////    soundFrom = accAmpDiff / abs(accAmpDiff);
    counter = 0;
    sumSoundRRaw = 0;
    sumSoundLRaw = 0;
//    lastDiff = finalDiff;
  }
  
//  Serial.print(abs(soundR) - abs(soundL));
  Serial.print(soundL);
  Serial.print(",");
  Serial.print(soundR);
//  Serial.print(",");
//  Serial.print(accAmpDiff);
//  Serial.print(",");
//  Serial.print(soundFrom);
  Serial.println();

//  lidServo.write(180.0);  
//  delay(1000);
//  lidServo.write(0);  
//  delay(1000);
//  if  (soundFrom == -1) {
//    lidServo.write(180.0);
//    delay(1000);
//  } else if (soundFrom == 1) {
//    lidServo.write(0);
//    delay(1000);
//  } 
  delay(1);
}
