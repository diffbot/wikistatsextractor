package com.diffbot.wikistatsextractor.extractors;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.diffbot.wikistatsextractor.dumpparser.DumpParser;
import com.diffbot.wikistatsextractor.util.Tokenizer;
import com.diffbot.wikistatsextractor.util.Util;

/**
 * Another important part of spotlight, produce a file, 
 * sfAndTotalCount that contains for each surface form the number of time
 * this SF is used as a surface form vs the number of time it appears in 
 * the text.
 * @author sam
 *
 */
public class ExtractAllNGrams {
	/** max nb token that a surface form can have */
	public static int MAX_NB_TOKEN = 4;
	/** local default language */
	public static String LOCALE="en_US";
	/** local language */
	public static String LANGUAGE="en";

	public static class AllNGramsWorker extends DumpParser.Worker {
		ConcurrentHashMap<String, Integer> all_interesting_sf;

		public AllNGramsWorker(ConcurrentHashMap<String, Integer> all_interesting_sf) {
			this.all_interesting_sf = all_interesting_sf;
		}

		@Override
		public void doSomethingWithPage(String page) {
			List<String> paragraphs = Util.getCleanTextFromPage(page, true, true, true,true);

			if (paragraphs == null)
				return;

			for (String paragraph : paragraphs) {
				ArrayList<Integer> delimiters = Tokenizer.getDelimiters2(paragraph, LANGUAGE);
				for (int i = 0; i < delimiters.size(); i += 2) {
					for (int j = 0; j < MAX_NB_TOKEN; j++) {
						if (i < delimiters.size() - j * 2 - 1) {
							String substr = paragraph.substring(delimiters.get(i), delimiters.get(i + 1 + 2 * j));
							Integer count=all_interesting_sf.get(substr);
							if (count!=null) {
									all_interesting_sf.put(substr, 1 + count);
							}
							
							// special case of the dot (Apple Inc.), if the next char is a dot, we also check it
							if (delimiters.get(i + 1 + 2 * j)<paragraph.length() && paragraph.charAt(delimiters.get(i + 1 + 2 * j))=='.'){
								substr = paragraph.substring(delimiters.get(i), delimiters.get(i + 1 + 2 * j)+1);
								count=all_interesting_sf.get(substr);
								if (count!=null) {
										all_interesting_sf.put(substr, 1 + count);
								}
							}
						}
					}
				}
			}

		}
	}

	public static void extractAllNGrams(String path_to_wiki_dump, String path_to_surface_form_file, String path_to_output) {
		/** get all the surface forms */
		ConcurrentHashMap<String, Integer> surface_forms=null;
		/** also keep in mind the surface forms that are all in lower case, for later */
		HashSet<String> lowercase_surface_forms=new HashSet<String>();
		try {
			surface_forms = new ConcurrentHashMap<String, Integer>(5000000,0.5f,7);
			BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(new File(path_to_surface_form_file)), "UTF8"), 16 * 1024);
			String line = br.readLine();
			while (line != null) {
				String[] split = Util.fastSplit(line);
				if (split.length != 2) {
					line = br.readLine();
					continue;
				}
				String sf = split[0];
				String lower_case = sf.toLowerCase();
				surface_forms.put(sf, 0);
				if (!sf.equals(lower_case))
					surface_forms.put(sf, 0);
				else
					lowercase_surface_forms.add(sf);
				line=br.readLine();
			}
			br.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		
		System.out.println(surface_forms.size() + " elements in the interesting sf");

		/** launch the dump Parsing */
		DumpParser dp = new DumpParser();
		for (int i = 0; i < 7; i++)
			dp.addWorker(new AllNGramsWorker(surface_forms));
		dp.extract(path_to_wiki_dump);

		/** creates the file we want */
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(new File(path_to_surface_form_file)), "UTF8"), 16 * 1024);
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(path_to_output)), "UTF8"));
			String line=br.readLine();
			while(line!=null){
				String[] split=Util.fastSplit(line);
				if (split.length==2){
					String sf=split[0];
					bw.write(sf+"\t"+split[1]+"\t"+surface_forms.get(sf)+'\n');
					if (!sf.equals(sf.toLowerCase()) && !lowercase_surface_forms.contains(sf.toLowerCase())){
						bw.write(sf.toLowerCase()+"\t-1\t"+surface_forms.get(sf)+'\n');
					}
					
				}
				line=br.readLine();
			}
			br.close();
			bw.close();
		} catch (IOException ioe) {
		}

	}

	public static void main(String[] args) {
		long start = System.currentTimeMillis();
		int level = 0;
		switch (level) {
		case 0:
			extractAllNGrams("enwiki_extract", "tmp_sf_counts_extract", "ngrams_extract");
			break;
		case 1:
			extractAllNGrams("frwiki-full", "tmp_sf_counts_fr", "ngrams_fr");
			break;
		case 2:
			extractAllNGrams("enwiki-full", "tmp_sf_counts_en", "ngrams_en");
			break;
		}
		System.out.println(System.currentTimeMillis() - start);
	}

}
