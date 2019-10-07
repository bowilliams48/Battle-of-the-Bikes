#include <SoftwareSerial.h>
#include <Servo.h>
#include <SPI.h>
#include "pins_arduino.h"
SoftwareSerial mySerial(8, 9);

int powerTime = 0;
int startPowerTime = 0;
boolean powerUp = false;
boolean otherPowerUp = false;

char myRot[1];
char otherRot[1];
String myString;
String otherString;

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
boolean valid;
boolean otherDone;
volatile byte otherCount;
volatile byte command = 0;
String toApp;

Servo myservo;
int curr = 180;
int pos;


void IRQcounter() {
    IRQcount++;
}  

int read_count() {
    noInterrupts();
    result = IRQcount;
    interrupts();
    return result;
}

void setup() { 
    mySerial.begin(9600);
    Serial.begin(9600); 
    Serial.setTimeout(20); 
    attachInterrupt(pin_irq, IRQcounter, RISING); 
    myservo.attach(10);
    pinMode(SS,INPUT);
    pinMode(SCK,INPUT);
    pinMode(MOSI,INPUT);
    pinMode(MISO,OUTPUT);
    
    digitalWrite(MISO,LOW);

    bothReady = false;
    valid = false;

    Serial.println("RESET");

    while(!valid) {
        valid = wait();
    }

    SPCR |= _BV(SPE);
    SPCR |= _BV(SPIE);
    
    while(!bothReady) {
        delay(10);
    }
    
    count = 0; 
    last_count = 0;
    new_counts = 0;
    result = 0;
    IRQcount = 0;
    done = false;
    otherDone = false;
}

boolean wait() {
    if(mySerial.available()) {
        int bytesRead = mySerial.readBytes(sBuff, BUFF_LEN);
        
        char weightArray[bytesRead];
        int count = 0;
        for(int i = 0; i < bytesRead; i++) {
            weightArray[count] = sBuff[i];
            count++;
        }
        double weight = 0;
        for(int i = count-1; i >= 0; i--) {
            weight += (weightArray[i]-'0')*pow(10, (count-1) - i);
        }

        Serial.write("Weight = ");
        Serial.println(weight, 0);
        return true;
    }
    return false;
}

ISR(SPI_STC_vect) {
    byte c = SPDR;
  
    Serial.println((char)c);

    switch (command) {
        case 0:
          command = c;
          SPDR = 0;
          break;
          
        case 'r':  
            if(done) {
                otherCount = SPDR;
                SPDR = 123; 
                if(otherCount == 123) {
                    otherCount = 0;
                    otherDone = true;
                }
            }
            else {
                otherCount = SPDR;
                if(powerUp) {
                    SPDR = 101;
                    powerUp = false;
                }
                else {
                    SPDR = (byte)new_counts; 
                }
                myString = String(new_counts);
                if(otherCount == 123) {
                    otherCount = 0;
                    otherDone = true;
                }
                otherString = String(otherCount);
                toApp = String(myString + ", " + otherString);
                mySerial.print(toApp);
            }
            command = 0;
            break;
          
        case 's':
            if(valid) {
                dist = (int)SPDR;
                SPDR = 100;
                mySerial.print("DISTANCE");
                mySerial.print(dist);
                bothReady = true;
            }
            else {
                SPDR = 0;
            }
            command = 0;
            break;

        case 'p':
            if(powerUp) {
                SPDR = 101;
                powerUp = false;
            }
            else {
                SPDR = (byte)new_counts;
            }
            Serial.println("PowerUp");
            otherPowerUp = true;
            startPowerTime = powerTime;
            myString = String(new_counts);
            otherString = String(otherCount);
            toApp = String(myString + ", " + otherString);
            mySerial.print(toApp);
            command = 0;
            break;
    }
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
    if (digitalRead (SS) == HIGH)
        command = 0;

    if(done && otherDone) {
        SPCR ^= _BV(SPIE);
        Serial.println("BOTH DONE");
        setup();
    }

    if(otherPowerUp && startPowerTime == powerTime) {
        incTension();
    }

    if(powerTime-5 == startPowerTime && startPowerTime != 0) {
        decTension();
        otherPowerUp = false;
    }

    count = read_count();  
    new_counts = count - last_count;   
    last_count = count;   

    digitalWrite(SS, LOW);


    if(mySerial.available()) {
        int bytesRead = mySerial.readBytes(sBuff, BUFF_LEN);

        if(bytesRead == 0) {
        }
        else if(bytesRead == 1 && sBuff[0] == '~') {
//            incTension();
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
    
    powerTime++;
    delay(1000);
}
