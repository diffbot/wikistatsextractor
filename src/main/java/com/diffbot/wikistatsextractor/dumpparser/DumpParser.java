package com.diffbot.wikistatsextractor.dumpparser;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

/**
 * Well, this class is at the core of the dump reading. It distributes the work on  
 * a big text file (or a list of text files)
 * 
 * To summarize it, a dumpparser launches: 
 * - A reader thread, that read a given text as fast as possible. It splits the big 
 * text files into "pages" (aka "chunks"), and put those pages into a ConcurrentQueue. 
 * - A few workers threads (you configure this) which pick pages from the queue and
 * process them. If an output is configured they can put String in a concurrent
 * queue and they will be written in output. 
 * - A writer thread which pick sentences from an output queue and write them in 
 * the file you specify.
 * 
 * Segmentation into pages can be done either by a certain number of line, or
 * using delimiters (by default "<page>" and "</page>".
 * 
 * It is not that intuitive, I recommend you to have a look at the class
 * DumpParserTutorial to get an idea of how you can use it.
 * 

 * 
 * @author sam
 *
 */
public class DumpParser {

	public static abstract class Worker implements Runnable {
		public ConcurrentLinkedQueue<String> pageQueue;
		public ConcurrentLinkedQueue<String> outputQueue;
		int element_written_in_output = 0;

		/**
		 * an array of size one which first element tells if the reader is
		 * currently reading
		 */
		public Boolean[] isReaderActive;

		/**
		 * an array of size one which first element tells if the worker is
		 * currently working
		 */
		public Boolean[] isWorkerActive = new Boolean[1];

		public void run() {
			isWorkerActive[0] = true;
			while (true) {
				if (pageQueue.isEmpty()) {
					if (isReaderActive[0]) {
						/** queue empty, we wait a bit for it to be filled */
						try {
							Thread.sleep(1);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						continue;
					} else {
						break;
					}
				}
				String page = pageQueue.poll();
				if (page == null)
					continue;

				/** actually does something with the page */
				doSomethingWithPage(page);
			}
			isWorkerActive[0] = false;
		}

		/** Override me, actualy do something with the page */
		public abstract void doSomethingWithPage(String page);

		/**
		 * write something in the output. By default, no "\n" is added
		 */
		public void writeInOutput(String s) {
			element_written_in_output++;
			if (element_written_in_output % 5000 == 0) {
				// not a constant time operation, hence we do it only once every
				// 5000 times
				int element_in_OQ = outputQueue.size();
				if (element_in_OQ > 20000) {
					// the output queue seems jammed, let's wait a bit.
					try {
						Thread.sleep(1);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					element_written_in_output--;
				}
			}
			outputQueue.add(s);
		}

	}

	String delimiter_start = "<page>";
	String delimiter_end = "</page>";

	/** set the delimiters for a page */
	public void setDelimiters(String start, String end) {
		this.delimiter_start = start;
		this.delimiter_end = end;
	}

	String path_to_output = null;

	/** whether or not we set an output */
	public void setAnOutput(String path_to_output) {
		this.path_to_output = path_to_output;
	}

	/** the list that contains the workers */
	List<Worker> workers = new ArrayList<Worker>();

	public void addWorker(Worker w) {
		workers.add(w);
	}

	public int getNbProcessor() {
		return Math.min(Runtime.getRuntime().availableProcessors(), 10);
	}

	/**
	 * if this is set to true, then the parser will not look for <page> </page>,
	 * but instead will throw "nb_lines_per_page" lines to each worker,
	 * considering it as a page
	 */
	boolean setSplitByNumberOfLine_ = false;

	public void setSplitByNumberOfLine(boolean set) {
		setSplitByNumberOfLine_ = set;
	}

	/** set the nb of line that we consider one page */
	int nb_lines_per_page = 500;

	public void setNumberOfLinePerPage(int nb_lines) {
		nb_lines_per_page = nb_lines;
	}

	/**
	 * The dump parser can deal with gzip, bzip or uncompressed files. This file
	 * select the right reader.
	 */
	public BufferedReader getDumpReader(String file)
			throws UnsupportedEncodingException, FileNotFoundException {
		// attempt to use gzip first
		BufferedReader br = null;
		try {
			br = new BufferedReader(new InputStreamReader(new GZIPInputStream(
					new FileInputStream(new File(file))), "UTF-8"), 16 * 1024);
		} catch (IOException e) {
			System.out.println(file
					+ " is not gzipped, trying bzip input stream..");
			try {
				FileInputStream fin = new FileInputStream(file);
				BufferedInputStream in = new BufferedInputStream(fin);
				BZip2CompressorInputStream bzIn = new BZip2CompressorInputStream(
						in);
				br = new BufferedReader(new InputStreamReader(bzIn));
			} catch (IOException f) {
				System.out.println(file
						+ " is not bzip commpressed, trying decompressed file");
				br = new BufferedReader(new InputStreamReader(
						new FileInputStream(new File(file)), "UTF8"), 16 * 1024);
			}
		}
		return br;
	}

	/** Main method, launches the dump parsing on a single file dump */
	public void extract(final String path_to_dump) {
		List<String> dumps_to_extract = new ArrayList<String>();
		dumps_to_extract.add(path_to_dump);
		extract(dumps_to_extract, Long.MAX_VALUE);
	}
	
	/** Main method, launches the dump parsing on a single file dump, allows to specify a max number of pages
	 * (for debug purposes) */
	public void extract(final String path_to_dump, long max_nb_pages_to_extract ) {
		List<String> dumps_to_extract = new ArrayList<String>();
		dumps_to_extract.add(path_to_dump);
		extract(dumps_to_extract, max_nb_pages_to_extract);
	}

	/**
	 * Same as above, but attempt to run the dump parser on multiple files, this
	 * way we could possibly store VERY big datasets, or merge efficiently
	 * datasets coming from different origins
	 */
	public void extract(final List<String> path_to_dumps, long max_nb_pages_to_extract) {
		extract(path_to_dumps.toArray(new String[1]), max_nb_pages_to_extract);
	}

	public void extract(final String[] path_to_dumps) {
		extract(path_to_dumps, Long.MAX_VALUE);
	}
	/**
	 * Same as above, but attempt to run the dump parser on multiple files, this
	 * way we could possibly store VERY big datasets, or merge efficiently
	 * datasets coming from different origins
	 */
	public void extract(final String[] path_to_dumps, final long max_nb_pages_to_extract) {
		long start = System.currentTimeMillis();

		ExecutorService es = Executors.newFixedThreadPool(getNbProcessor());

		if (workers.size() == 0) {
			System.out.println("No workers have been specified ");
			return;
		}

		/**
		 * execute the thread that looks greedily for <page> and </page>, and
		 * send the String in between to a queue
		 */
		final ConcurrentLinkedQueue<String> pageQueue = new ConcurrentLinkedQueue<String>();
		final ConcurrentLinkedQueue<String> output = new ConcurrentLinkedQueue<String>();
		final Boolean[] isReaderActive = new Boolean[1];
		isReaderActive[0] = true;

		

		/**
		 * first thread to be executed, the reader. This things reads as fast as
		 * it can (well, it's not fully optimised right now). There are two
		 * logic, that I separated (I think it is clearer that way)
		 */
		if (setSplitByNumberOfLine_) {
			// in this case, we read and send the text to the workers every
			// "nb_lines_per_page" lines
			es.execute(new Runnable() {
				public void run() {
					try {
						long counter_pages=0;
						for (final String path_to_dump : path_to_dumps) {
							System.out.println("start parsing the dump: "
									+ path_to_dump);
							File f = new File(path_to_dump);
							if(!f.exists() || f.isDirectory()) { 
							    System.out.println(path_to_dump+" does not exist or is a directory. Ignored.");
							    continue;
							}
							System.out.println();
							BufferedReader br = getDumpReader(path_to_dump);
							String line = br.readLine();
							StringBuilder sb = new StringBuilder();
							while (line != null) {
								counter_pages++;
								if (counter_pages % nb_lines_per_page == 0) {
									pageQueue.add(sb.toString());
									// check if the queue is full
									if (counter_pages % nb_lines_per_page * 300 == 0) {
										while (pageQueue.size() > 300) {
											/** page Queue is full */
											try {
												Thread.sleep(1);
											} catch (InterruptedException e) {
												e.printStackTrace();
											}
										}
									}
									if (counter_pages % 100000 == 0) {
										String display = "\rParsed " + counter_pages
												+ " pages";
										System.out.print(display);
									}
									if (counter_pages>max_nb_pages_to_extract){
										break;
									}
									sb.setLength(0);
								}
								sb.append(line);
								sb.append('\n');
								line = br.readLine();
							}
							// we send the remaining things to the workers
							pageQueue.add(sb.toString());
							br.close();
						}

					} catch (IOException ioe) {
						ioe.printStackTrace();
					}
					isReaderActive[0] = false;
				}
			});
		} else {
			// second case, each page is something comprised between <page> and
			// </page>
			es.execute(new Runnable() {
				public void run() {
					try {
						long counter_pages=0;
						for (final String path_to_dump : path_to_dumps) {
							System.out.println("start parsing the dump: "
									+ path_to_dump);
							File f = new File(path_to_dump);
							if(!f.exists() || f.isDirectory()) { 
							    System.out.println(path_to_dump+" does not exist or is a directory. Ignored.");
							    continue;
							}
							System.out.println();
							BufferedReader br = getDumpReader(path_to_dump);
							String line = br.readLine();
							boolean page_started = false;
							StringBuilder sb = new StringBuilder();
							while (line != null) {
								if (line.contains(delimiter_end)) {
									page_started = false;
									pageQueue.add(sb.toString());
									counter_pages++;
									if (counter_pages % 10000 == 0) {
										while (pageQueue.size() > 10000) {
											/** page Queue is full */
											try {
												Thread.sleep(1);
											} catch (InterruptedException e) {
												e.printStackTrace();
											}
										}
									}
									if (counter_pages % 100000 == 0) 
										System.out.print("\rParsed " + counter_pages + " pages");
									if (counter_pages>max_nb_pages_to_extract){
										break;
									}
									sb.setLength(0);
								}
								if (page_started) {
									sb.append(line);
									sb.append("\n");
								}
								if (line.contains(delimiter_start)) {
									page_started = true;
								}
								line = br.readLine();
							}
							br.close();
						}

					} catch (IOException ioe) {
						ioe.printStackTrace();
					}
					isReaderActive[0] = false;
				}
			});

		}

		/** then add the workers */
		for (Worker w : workers) {
			w.pageQueue = pageQueue;
			w.isReaderActive = isReaderActive;
			if (path_to_output != null) {
				w.outputQueue = output;
			}
			es.execute(w);
		}

		/**
		 * finally, if the user has specified an output, we write it immediately
		 */
		if (path_to_output != null) {
			final String path_to_output_final = path_to_output;
			es.execute(new Runnable() {
				public void run() {
					try {
						BufferedWriter bw = new BufferedWriter(
								new OutputStreamWriter(new FileOutputStream(
										new File(path_to_output_final)), "UTF8"),
								16 * 1024);
						while (true) {
							String s = output.poll();
							if (s == null) {
								try {
									Thread.sleep(1);
								} catch (InterruptedException e) {
									e.printStackTrace();
								}
								if (!isReaderActive[0] && output.isEmpty()) {
									// so the reader has finished. But did the
									// worker finish?
									boolean all_workers_finished = true;
									for (Worker w : workers) {
										if (w.isWorkerActive[0]) {
											all_workers_finished = false;
										}
									}
									try {
										Thread.sleep(100);
									} catch (InterruptedException e) {
										e.printStackTrace();
									}
									if (!isReaderActive[0]
											&& all_workers_finished
											&& output.isEmpty()) {
										break;
									}
								} else {
									continue;
								}

							} else {
								bw.write(s);
							}
						}
						bw.flush();
						bw.close();
					} catch (IOException ioe) {
						ioe.printStackTrace();
					}
				}
			});
		}

		es.shutdown();
		while (!es.isTerminated()) {
			try {
				es.awaitTermination(50, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		/** We are done */
		System.out.println("done in " + (System.currentTimeMillis() - start)
				+ "ms");
	}

}
