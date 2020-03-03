rm -rf ./out
javac -d ./out --class-path ./out:./lib/junit-platform-console-standalone-1.6.0.jar:./lib/commons-lang3-3.9.jar:./lib/annotations-18.0.0.jar ./src/Price.java ./test/PriceTest.java
java -jar ./lib/junit-platform-console-standalone-1.6.0.jar --class-path ./out:./lib/annotations-18.0.0.jar:./lib/commons-lang3-3.9.jar --scan-class-path
