/*
 * Copyright 2017 John Boyer
 *
 *	Licensed under the Apache License, Version 2.0 (the "License");
 *	you may not use this file except in compliance with the License.
 *	You may obtain a copy of the License at
 *
 *		http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software
 *	distributed under the License is distributed on an "AS IS" BASIS,
 *	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *	See the License for the specific language governing permissions and
 *	limitations under the License.
 */
package me.johnboyer.image.tools;


import static me.johnboyer.image.tools.ElasticsearchContext.BULK_UPLOAD_FORMAT;
import static me.johnboyer.image.tools.ElasticsearchContext.INDEX_DEF_JSON;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.FileBasedConfiguration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.exception.ContextedRuntimeException;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONObject;

import com.flickr4java.flickr.Flickr;
import com.flickr4java.flickr.FlickrException;
import com.flickr4java.flickr.REST;
import com.flickr4java.flickr.photos.Photo;
import com.flickr4java.flickr.photos.PhotoList;
import com.flickr4java.flickr.photos.PhotosInterface;
import com.flickr4java.flickr.photos.SearchParameters;
import com.flickr4java.flickr.test.TestInterface;

import me.johnboyer.image.location.Place;

/**
 * Flickr API Elasticsearch indexer application
 * @author John Boyer
 * @version 2017-06-13
 *
 */
public class FlickrElasticsearchIndexerApp {
	
	/**
	 * Small image width string
	 * @see https://www.flickr.com/services/api/flickr.photos.getSizes.html
	 */
	private static final String IMG_WIDTH_STR = Integer.valueOf(320).toString();
	/**
	 * Small image height string
	 * @see https://www.flickr.com/services/api/flickr.photos.getSizes.html
	 */
	private static final String IMG_HEIGHT_STR = Integer.valueOf(240).toString();
	/**
	 * Log object
	 */
	private static final Logger LOG = Logger.getLogger(FlickrElasticsearchIndexerApp.class);
	/**
	 * Photos per page
	 */
	private static final int PHOTOS_PER_PAGE = 250;
	
	
	/**
	 * Writes the Flickr search results for the given place to JSON file with following format:
	 * <code>place-name_photos.json</code>.
	 * @param f Flickr API object
	 * @param place Place object
	 */
	private static void writeSearchToElasticsearchBulkJSON(Flickr f, Place place) {
		
		LOG.info("Building bulk index file for " + place.getName() + "...");

		PhotosInterface p = f.getPhotosInterface();
		SearchParameters params = new SearchParameters();
		params.setPlaceId(place.getId());
		
		try {
			
			final int startPage = 1;
			LOG.debug(PHOTOS_PER_PAGE + " photos per page");
			PhotoList<Photo> photos = p.search(params, PHOTOS_PER_PAGE, startPage);
			int totalPages = photos.getPages();
			LOG.debug("Search contains " + totalPages + " pages");

			PrintWriter out = new PrintWriter(place.getName().trim()  + "_photos.json");
			Set<String> uniquePhotos = new HashSet<>();
			
			for (int i = startPage; i < totalPages; i++) {
				LOG.trace("Processing page " + i);
				
				//Total number of pages can vary between calls
				photos = p.search(params, PHOTOS_PER_PAGE, i);
				if(photos.getPages() < totalPages) {
					totalPages = photos.getPages();
					LOG.debug("Search contains " + totalPages + " pages");
				}
				
				//Iterate through the photos and write to the JSON file
				Iterator<Photo> it = photos.iterator();
				while(it.hasNext()) {
					Photo photo = it.next();
					final String smallUrl = photo.getSmall320Url();
					
					//Flickr's API returns duplicates, so we ensure that the photo hasn't already been added.
					if(!uniquePhotos.contains(smallUrl)) {
						JSONObject json = getPhotoJSONObject(photo, smallUrl);
						uniquePhotos.add(smallUrl);
						
						//Write index JSON to file 
						out.println(INDEX_DEF_JSON);
						out.println(json.toString());
					}
				}
			}
			
			LOG.debug("uniquePhotos.size() = " + uniquePhotos.size());
			
			//Cleanup resources
			out.flush();
			uniquePhotos.clear();
			IOUtils.closeQuietly(out);
			
			LOG.info("Finished building bulk index file");
			
			
		} catch (FlickrException | IOException e) {
			LOG.error("API Processing Error", e);
		}
	}

	private static JSONObject getPhotoJSONObject(Photo photo, final String smallUrl) {
		Map<String, String> photoMap = new HashMap<>();
		photoMap.put("title", photo.getTitle());
		photoMap.put("smallUrl", smallUrl);
		photoMap.put("height", IMG_HEIGHT_STR);
		photoMap.put("width", IMG_WIDTH_STR);
		JSONObject json = new JSONObject(photoMap);
		return json;
	}
	
	public static void main(String[] args) {
		
		if(args.length != 1) {
			usage();
			
		} else if(args[0].equals("generate")) {
			
			Configuration config = null;
			try {
				config = initProperties();				
			} catch (ConfigurationException e) {
				System.err.println(e.getMessage());
				LOG.fatal("Unable to initialize app properties", e);
				System.exit(-1);
			}
			
			LOG.info("Connecting to Flickr");
			Flickr f = new Flickr(config.getString("flickr.apiKey"), config.getString("flickr.sharedSecret"), new REST());
			
			testFlickrConnection(f);
			generateBulkIndexFiles(f);
			
		} else if(args[0].equals("upload")) {
			bulkUploadIndexFiles();
			
		} else {
			System.err.println("Unknown command");
			usage();
		}
	}

	private static Configuration initProperties() throws ConfigurationException {
		//Log4j
		PropertyConfigurator.configure(FlickrElasticsearchIndexerApp.class.getClassLoader().getResource("log4j.properties"));
		
		//Flickr
		Parameters params = new Parameters();
		FileBasedConfigurationBuilder<FileBasedConfiguration> builder;
		builder = new FileBasedConfigurationBuilder<FileBasedConfiguration>(PropertiesConfiguration.class)
		                       .configure(params.properties()
		                       .setFileName("flickr.properties"));
		return builder.getConfiguration();
	}

	private static void usage() {
		System.err.println("Usage: generate | upload");
	}

	public static void bulkUploadIndexFiles() {
		LOG.warn("Uploading index files...");
		
		try {
			//Build a bash script to upload indexes
			final String fileName = "es-indexer.sh";
			PrintWriter pw = new PrintWriter(fileName);
			
			pw.println("echo \"Uploading index files...\"");
			final String curlCliFormat = "curl -XPOST %s %s";

			Place[] places = getDefaultPlaces();
			for (Place place : places) {
				String cli = String.format(curlCliFormat, BULK_UPLOAD_FORMAT, "\"@" + place.getName() + "_photos.json\"");
				pw.println(cli);
			}
			
			pw.println("echo \"Finished uploading index file\"");			
			IOUtils.closeQuietly(pw);
			
			//Set bash script as executable
			File file = new File(fileName);
			file.setExecutable(true);
			
			//Execute the bash script
			CommandLine command = CommandLine.parse("./" + fileName);
			DefaultExecutor dex = new DefaultExecutor();
			dex.setExitValue(0);
			int exit = dex.execute(command);
			if(exit != 0) {
				LOG.warn("Execution returned non-zero value");
			} else {
				LOG.info("Successfully uploaded index files");
			}
			
		} catch (FileNotFoundException e) {
			LOG.error("Unable to create file", e);
		} catch (ExecuteException e) {
			LOG.error("Execution failed", e);
		} catch (IOException e) {
			LOG.error("I/O error", e);
		}
	}

	public static void generateBulkIndexFiles(Flickr f) {
		Place[] places = getDefaultPlaces();
		for (Place place : places) {
			new Thread(() -> {
				writeSearchToElasticsearchBulkJSON(f, place);
			}, "Thread-"+place.getName()).start();
		}
	}
	
	@SuppressWarnings("unchecked")
	private static void testFlickrConnection(Flickr f) {
		LOG.debug("Testing Flickr connection");
		TestInterface testInterface = f.getTestInterface();
		try {
			testInterface.echo(Collections.EMPTY_MAP);
		} catch (FlickrException e) {
			LOG.error("Flickr API Error", e);
			throw new ContextedRuntimeException("Flickr API Error", e);
		}
	}
	
	/**
	 * @return The default places for the app.
	 */
	private static Place[] getDefaultPlaces() {
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
				           
		};
		return places;
	}

}
