# wikistatsextractor
A small tool that [Diffbot](http://www.diffbot.com) uses to extract statistics from Wikipedia Dump files for its [Global Index](http://www.diffbot.com/products/globalindex/).

## What it does

Initially developed to efficiently provide the statistics required by [Dbpedia spotlight](https://github.com/dbpedia-spotlight/dbpedia-spotlight/wiki), this tool stays general, meaning you can use it to extract information from text files of an order of magnitude of 100GB.

At the moment it can be used to extract statistics from the [Wikpedia dump](https://dumps.wikimedia.org/enwiki/latest/). The English dump, once uncompressed, is around 60GB. wikipediastatsextractor takes between 5 and 10mn, on an SSD to traverse it. Since we traverse it three times (first to extract the uris and redirections, then to extract the surface forms counts, and then to extract the token counts), to produce all of the statistics required for dbpedia spotlight. This typically takes 30 minutes on a newer laptop. (16GB RAM, 512GB SSD, 4cores/8threads processor). 

## How to use it?

You will need at least 14GB of RAM to execute this program, and around 100GB of SSD
- Clone this git
- Download the latest wikipedia dump https://dumps.wikimedia.org/enwiki/latest/, look for enwiki-latest-pages-articles.xml.bz2. Uncompress it.
- (If not done already download maven)
- Compile (mvn compile)
- checkout the configuration file in /conf. Adapt it to point to the uncompressed dump
- Extend the RAM used by java to at least 14GB: ```export MAVEN_OPTS="-Xmx14g"```
- Run the script. From the project root: ```mvn exec:java```
- The script will take around 30 min to produce the output (by default in data/output), and some temporary files (among others, the redirections) in the tmp folder (by default in data/tmp).


## What does it produce?
Right now mainly the same 4 files initially produced by pignlproc for dbpedia spotlight.

File  | Line Format
------------- | -------------
uriCount  | ```<title>\t<uri>\t<count>```
pairCount  | ```<surface form>\t<uri>\t<count>```
sfAndTotalCount  | ```<surface form>\t<count as SF>\t<count as token>```
tokenCount  | ```<uri (wikipedia style)>\t{(context_token1,count1),(context_token2, count2),... }```



## License 
The MIT License (MIT)

Copyright (c) 2015 Diffbot

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
