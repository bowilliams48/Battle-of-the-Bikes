#include <SoftwareSerial.h>
#include <Servo.h>
#include <SPI.h>
#include "pins_arduino.h"
SoftwareSerial mySerial(8, 9);

int powerTime = 0;
int startPowerTime = 0;
boolean powerUp = false;
boolean otherPowerUp = false;

volatile int IRQcount;
int pin = 2;
int pin_irq = 0; 

const int BUFF_LEN = 21; 

char sBuff[BUFF_LEN]; 

int count;
int last_count;
int new_counts;
int result;
int dist;
boolean done;
boolean bothReady;
boolean otherDone;
volatile byte otherCount;

Servo myservo;
int curr = 180;
int pos;


void IRQcounter() 
{
  IRQcount++;
}  

int read_count()
{
  noInterrupts();
  result = IRQcount;
  interrupts();
  return result;
}

byte transferAndWait (const byte what)
{
    byte a = SPI.transfer (what);
    delayMicroseconds (100);
    return a;
} 

boolean waitUntilReady() {
    Serial.println("WAITING");
    digitalWrite(SS, LOW);
    transferAndWait('s');
    transferAndWait(dist);
    otherCount = transferAndWait(0);
    Serial.println(otherCount);
    if(otherCount == 100) {
      return true;
    }
    digitalWrite(SS, HIGH);
    return false;
}

void setup() { 
    mySerial.begin(9600);
    Serial.begin(9600); 
    Serial.setTimeout(20); 
    attachInterrupt(pin_irq, IRQcounter, RISING); 
    myservo.attach(10);
    pinMode(SS,OUTPUT);
    pinMode(SCK,OUTPUT);
    pinMode(MOSI,OUTPUT);
    pinMode(MISO,INPUT);

    digitalWrite(SS,HIGH);
    digitalWrite(SCK,LOW);
    digitalWrite(MOSI,LOW);

    SPI.begin ();
    SPI.setClockDivider(SPI_CLOCK_DIV8);

    Serial.println("RESET");
 
    boolean valid = false;
    bothReady = false;
    
    while(!valid) {
        valid = wait();
    }
    while(!bothReady) {
        bothReady = waitUntilReady();
        delay(10);
    }
    mySerial.print("Ready!");
    count = 0; 
    last_count = 0;
    new_counts = 0;
    result = 0;
    IRQcount = 0;
    done = false;
    otherDone = false;
    otherCount = 0; 
}

boolean wait() {
    if(mySerial.available()) {
        int bytesRead = mySerial.readBytes(sBuff, BUFF_LEN);
        
        int at;

        for(int i=0; i < bytesRead; i++) {
            if(sBuff[i] == ',') {
                at = i;
                break;
            }
        }
        char distArray[at+1];
        for(int i = 0; i < at; i++) {
            distArray[i] = sBuff[i];
        }
        dist = 0;
        for(int i = at-1; i >= 0; i--) {
            dist += (distArray[i]-'0')*(int)pow(10, (at-1) - i);
        }
        char weightArray[bytesRead-(at+2)];
        int count = 0;
        for(int i = at+2; i < bytesRead; i++) {
            weightArray[count] = sBuff[i];
            count++;
        }
        double weight = 0;
        for(int i = count-1; i >= 0; i--) {
            weight += (weightArray[i]-'0')*pow(10, (count-1) - i);
        }
        Serial.write("Dist = ");
        Serial.println(dist);
        Serial.write("Weight = ");
        Serial.println(weight, 0);
        return true;
    }
    return false;
}

void incTension() {
    Serial.write("Increase the tension\n");

    int goTo = curr - 5;

    for (pos = curr; pos >= goTo; pos -= 1) { 
        curr--;
        myservo.write(pos);              
        delay(15);                       
    }
}

void decTension() {
    Serial.write("Decrease the tension\n");

    int goTo = curr + 5;

    for (pos = curr; pos >= goTo; pos += 1) { 
        curr++;
        myservo.write(pos);              
        delay(15);                       
    }
}

void loop() {

    count = read_count();
    new_counts = count - last_count;
    last_count = count;

    if(otherPowerUp && startPowerTime == powerTime-1) {
        incTension();
    }

    if(powerTime-5 == startPowerTime && startPowerTime != 0) {
        decTension();
        otherPowerUp = false;
    }

    if(done && otherDone) {
        digitalWrite(SS, LOW);
        transferAndWait('r');
        transferAndWait(123);
        otherCount = transferAndWait(0);
        if(otherCount == 123) {
            otherDone = true;
            otherCount = 0;
        }
        if(otherCount == 101) {
            Serial.println("PowerUp");
            startPowerTime = powerTime;
            otherPowerUp = true;
            otherCount = 0;
        }
        digitalWrite(SS, HIGH);
        Serial.println("BOTH DONE");
        setup();
    }

    else if(done) {
        digitalWrite(SS, LOW);
        transferAndWait('r');
        transferAndWait(123);
        otherCount = transferAndWait(0);
        if(otherCount == 123) {
            otherDone = true;
            otherCount = 0;
        }
        if(otherCount == 101) {
            Serial.println("PowerUp");
            startPowerTime = powerTime;
            otherPowerUp = true;
            otherCount = 0;
        }
        digitalWrite(SS, HIGH);
    }
    else if(powerUp) {
        digitalWrite(SS, LOW);
        transferAndWait('p');
        transferAndWait(101);
        otherCount = transferAndWait(0);
        if(otherCount == 123) {
            otherDone = true;
            otherCount = 0;
        }
        if(otherCount == 101) {
            Serial.println("PowerUp");
            startPowerTime = powerTime;
            otherPowerUp = true;
            otherCount = 0;
        }
        digitalWrite(SS, HIGH);
        powerUp = false;
    }
    else {
        digitalWrite(SS, LOW);
        transferAndWait('r');
        transferAndWait((byte)new_counts);
        otherCount = transferAndWait(0);
        if(otherCount == 123) {
            otherDone = true;
            otherCount = 0;
        }
        if(otherCount == 101) {
            Serial.println("PowerUp");
            startPowerTime = powerTime;
            otherPowerUp = true;
            otherCount = 0;
        }
        digitalWrite(SS, HIGH);

        String myString = String(new_counts);
        String otherString = String(otherCount);
        String toApp = String(myString + ", " + otherString);

        mySerial.print (toApp);

 
        if(mySerial.available()) {
            int bytesRead = mySerial.readBytes(sBuff, BUFF_LEN);
    
            if(bytesRead == 0) {
            }
            else if(bytesRead == 1 && sBuff[0] == '~') {
//                incTension();
                powerUp = true;
                
                /*
                 * 
                 * Need to send value over to other bike to change its tension.
                 * 
                 */
            }
            else if(bytesRead == 1 && sBuff[0] == '^') {
                incTension();
            }
            else if(bytesRead == 1 && sBuff[0] == '!') {
                done = true;
                Serial.println("IM DONE");
                for (pos = curr; pos <= 180; pos += 1) { 
                    curr++;
                    myservo.write(pos);              
                    delay(15);                       
                }
            }
        }
    }

    powerTime++;
    delay(1000);
}
