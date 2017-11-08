# WebIntelligence

Team : Leo Pernelle, Jean Brute de Remur, Erick Taru, Jean Miquel, Baptiste Vautrin


## What we use 
- Scala
- Spark
- SBT
- MLlib


## Prerequirements

- JDK 8
- Scala 2.11
- Spark 2.1.0
- Sbt 1.0.3

- Install IntelliJ : https://www.jetbrains.com/idea/


## How to run

### clone the project

`git clone https://github.com/Vautrinss/WI.git`

### open IntelliJ and follow those instructions :

0) If it's the first time running IntelliJ, install Scala plugin when asked


1) Click on import project

[![Capture1.png](https://s1.postimg.org/6aqrniufvj/Capture1.png)](https://postimg.org/image/9cmnoqvs2j/))

2) Select the cloned project

[![Capture2.png](https://s1.postimg.org/73hoh8jbb3/Capture2.png)](https://postimg.org/image/48p0bg44jf/)

3) Open it as a SBT project

[![Capture3.png](https://s1.postimg.org/4fs86vwa0v/Capture3.png)](https://postimg.org/image/16t4a88se3/)

4) Select your JDK for the project

[![Capture4.png](https://s1.postimg.org/51en7co8in/Capture4.png)](https://postimg.org/image/96a8jglel7/)

5) Press ALT+SHIFT+F10, then Edit Configuration 

! make sure your Main Class is MainApp and your data set path is in Program Arguments !

[![Capture5.png](https://s1.postimg.org/39keemxjkv/Capture5.png)](https://postimg.org/image/1ao7oas197/)

6) Run the MainApp : right click on MainApp --> Run

Wait for the exit code 0

## Results 

The CSV file is stored in the folder **ouput** at the root of the project

The file is named like part-00000-xxx...
