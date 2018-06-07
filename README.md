# bibsonomy-uploader
Our simple Java command line tool for uploading bibtex files to bibsonomy

## Build
Its plain maven.

```bash
mvn clean install
```

## Usage

A bundled jar file is generated under `bibsonomy-uploader-debian-cli/target`, therfore, from the project root directory it can be run with:


```bash
java -cp `find bibsonomy-uploader-debian-cli/target -name 'bibsonomy-uploader*jar'` org.aksw.bibuploader.BibUpdater username apikey apiurl bibtex-file
```
## Building the jar

mvn clean compile assembly:single

## Note, only executable with Java 1-8-101 or higher

java --add-modules java.xml.bind -jar target/bibsonomy-uploader-cli-0.9.0-SNAPSHOT-jar-with-dependencies.jar aksw "insertAPIkeyHERE" "http://www.bibsonomy.org/api" ~/Papers/bib/aksw.bib 

## Debian Package

### Removing and (re-)installing the Debian package

```
sudo apt-get purge aksw-bibsonomy-uploader

sudo dpkg -i `find bibsonomy-uploader-debian-cli/target -name '*.deb'`

# This command is now available:
aksw-bibsonomy-uploader
```

