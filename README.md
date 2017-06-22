# img-indexer

## Overview
The **Image Indexer** is a standalone sample Java application that downloads photo metadata from the Flickr API and uploads it to AWS Elasticsearch (ES) index.

It downloads metadata for the following places around the world:

	Place[] places = { 
				    new Place("Seattle", "uiZgkRVTVrMaF2cP"), 
				    new Place("San Francisco", "7.MJR8tTVrIO1EgB"),
				    new Place("Portland", "Oc0MVktTVr1JQ7P5"),
				    new Place("Los Angeles", "7Z5HMmpTVr4VzDpD"),
				    new Place("Chicago", "prbd60NTUb2haaDH"),
				    new Place("New York", "ODHTuIhTUb75gdBu"),
				    new Place("London", "hP_s5s9VVr5Qcg"),
				    new Place("Paris", "EsIQUYZXU79_kEA"),
				    new Place("Shanghai", "JAJiM7JTU78IjzqC"),
				    new Place("Florence", "ZPDshblWU7_DgSs"),
				    new Place("Rome", "uijRnjBWULsQTwc"),
				    new Place("Hong Kong", "4Jji9AVTVrLRrBR9Zg"),
				    new Place("Barcelona", "p.gvf6dWV7k1Gpg")
				    }
				    
After it completes downloading, it writes an AWS ES index entry to a JSON file for bulk upload later. For example:

	{"index":{ "_index":"flickr","_type":"photo"}}
	{"smallUrl":"https://farm5.static.flickr.com/4211/34364073364_e1ed834920_n.jpg","title":"Seattle 	Skyline", ... }

![Seattle Skyline](https://farm5.static.flickr.com/4211/34364073364_e1ed834920_n.jpg)

## Usage
1. Create and configure an AWS ES domain. Learn more [here](http://docs.aws.amazon.com/elasticsearch-service/latest/developerguide/es-createupdatedomains.html).
2. Update the endpoint in `me.johnboyer.image.tools.ElasticsearchContext`, e.g., `ENDPOINT = https://search-movies-4f3nw7eiia2xiynjr55a2nao2y.us-west-1.es.amazonaws.com`
3. Signup for Flickr API keys at https://www.flickr.com/services/api
5. Update the `flickr.properties` file at `src/main/java`.
6. Build the project, generate the JSON files, and upload them to AWS ES.

		gradle build
		java -jar build/libs/img-indexer-1.0-alpha1.jar generate
		java -jar build/libs/img-indexer-1.0-alpha1.jar upload
	
7. After the upload command has completed, login into AWS ES. In the dashboard, click the *domain* > Indices > flickr and review the `photo` mappings.

## License
This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
