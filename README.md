# wikistatsextractor
A small tool made to extract statistics from Wikipedia Dump files.

## What it does

Initially developped to provide efficiently the statistics required by [Dbpedia spotlight](https://github.com/dbpedia-spotlight/dbpedia-spotlight/wiki), this tool stays general, meaning you can use it to extract information from text files of an order of magnitude of 100GB.

At the moment is can be used to extract statistics from the [Wikpedia dump](https://dumps.wikimedia.org/enwiki/latest/). The English dump, once uncompressed, weights around 60GB. wikipediastatsextractor takes between 5 and 10mn, on an SSD to traverse it. Since we traverse it three times (first to extract the uris and redirections, then to extract the surface forms counts, then to extract the token counts), producing all statistics required for dbpedia spotlight typically takes 30mn on a recent laptop. (16GB RAM, 512GB SSD, 4cores/8threads processor). 

## How to use it?

You will need at least 14GB of RAM to execute this program, and around 100GB of SSD
- Clone this git
- Download the latest wikipedia dump https://dumps.wikimedia.org/enwiki/latest/, look for enwiki-latest-pages-articles.xml.bz2. Uncompress it.
- (If not done already download maven)
- Compile (mvn compile)
- checkout the configuration file in /conf. Adapt it to point to the uncompressed dump
- Extend the RAM used by java to at least 14GB: ```export MAVEN_OPTS="-Xmx14g"```
- Run the script. From the project root: ```mvn exec:java```
- The script will take around 30mn to produce the output (by default in data/output), and some temporary files (among others, the redirections) in the tmp folder (by default in data/tmp).


## What does it produce?
Right now mainly 4 files:
File  | Line format
------------- | -------------
uriCount  | ```<title>\t<uri>\t<count>```
pairCount  |  ```<surface form>\t<uri>\t<count>```
sfAndTotalCount  |  ```<surface form>\t<count as SF>\t<count as token>```
tokenCount  |  ```<uri>\t{(context_token1,count1),(context_token2, count2),... }```
