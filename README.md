## CS221 Project - Peterman Search Engine

This repository contains the starter skeleton code for the [CS221 project](https://grape.ics.uci.edu/wiki/public/wiki/cs221-2019-spring).

### Finished tasks:
##### [Project 1](https://grape.ics.uci.edu/wiki/public/wiki/cs221-2019-spring-project1)

- Task 1: Implement a simple tokenizer based on punctuations and white spaces
- Task 2: Implement a Dynamic-Programming based Word-Break Tokenizer 
- Task 3: Incorporate a Porter stemmer
- Task 4: Implement a dynamic-programming based Japanese tokenizer

##### [Project 2](https://grape.ics.uci.edu/wiki/public/wiki/cs221-2019-spring-project2)

- Task 1: Implement LSM-like disk-based inverted index that supports insertions.
- Task 2: Implement merge of inverted index segments so that the number of segments would not grow indefinitely.
- Task 3: Implement keyword search, boolean AND search, and boolean OR search.

##### [Project 3](https://grape.ics.uci.edu/wiki/public/wiki/cs221-2019-spring-project3)

- Task 1: Implement LSM-like Positional Index to store the positions of all the occurrences of a keyword in a document
- Task 2: Implement Phrase Search (`phrase` represents a consecutive sequence of keywords)
- Task 3: Implement compression based on delta encoding and variable-length encoding. Delta encoding means storing the difference (delta) between adjacent integers rather than the actual numbers. Variable-length encoding of an integer works similarly to UTF-8.

##### [Project 4](https://grape.ics.uci.edu/wiki/public/wiki/cs221-2019-spring-project4)

- Task 1: Implement TF-IDF ranking (Cosine) to return the top-K documents ranked by TF-IDF scores.
- Task 2: Implement PageRank and run with ICS websites.

### To run this example:
1. clone this repository using `git clone https://github.com/UCI-Chenli-teaching/spring19-cs221-project.git`
1. run `mvn clean install -DskipTests` in command line
1. open IntelliJ -> Open -> Choose the directory. Wait for IntelliJ to finish importing and building.
1. You can run the `HelloWorld` program under `src/main/java/edu.uci.ics.cs221` package to test if everything works.
