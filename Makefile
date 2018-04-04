test: clean
	mvn verify

clean:
	mvn clean

publish: clean
	mvn -Prelease deploy

