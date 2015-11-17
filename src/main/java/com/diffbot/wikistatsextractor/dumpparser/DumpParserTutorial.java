package com.diffbot.wikistatsextractor.dumpparser;



/** demo class designed as a tutorial for how to use the dumpParser */
public class DumpParserTutorial {
	
	/** Demonstration worker, it simply write the first line of each page in the output */
	public static class TestWorker extends DumpParser.Worker {
		@Override
		public void doSomethingWithPage(String page) {
			if (page==null)
				return;
			String[] all_lines=page.split("\n");
			if (all_lines.length>0)
				writeInOutput(all_lines[0]+"\n");
		}
	}
	
	/** main method, take as input "bigfile.txt", suppose it is segmented in pages
	 *  delimited by "<page>" and "</page>", and write the first line of each page in the output */
	public static void main(String[] args){
		DumpParser dp=new DumpParser();
		
		/** set the delimiters. By default it is already "<page>" and "</page>" */
		dp.setDelimiters("<page>", "</page>");

		/** if you wanted to split the big file by lines (each page is 500 page, for example) */
		// dp.setSplitByNumberOfLine(true);
		// dp.setNumberOfLinePerPage(500);
		
		/** add the workers */
		for (int i=0; i<6; i++){
			dp.addWorker(new TestWorker());
		}
		
		/** set an output */
		dp.setAnOutput("path/to/output.txt");
		
		/** launch extraction */
		dp.extract("path/to/bigfile.txt");
	}

}
