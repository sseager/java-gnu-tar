package com.ice.tar;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.ice.tar.TarEntry;
import com.ice.tar.TarInputStream;
import com.ice.tar.TarOutputStream;


/**
 * This is a utility class which can create and extract tar or tar.gz files.
 * For tar.gz, the returned tar file would have to be run through the gzip method.
 * 
 * This library is under the Apache License Version 2.0
 * 
 * Authors:
 * 
 * @author jeremy Lucier
 *
 */
public class Tar {

	private static final int BUFFER_SIZE = 1024;
	private static final Logger logger = Logger.getLogger(Tar.class.getName());

	/**
	 * Extracts files from a tar or gzip file to a destination directory
	 * @param srcTarFile
	 * @param destDirectory
	 * @throws IOException
	 */
	public static void extractFiles(File srcTarOrGzFile, File destDirectory) throws IOException{

		// destFolder needs to be a directory
		if(destDirectory.exists() && destDirectory.isFile()) {
			throw new IOException("Destination is not a directory!");
		} else if(destDirectory.exists() == false) {

			// Make the destination directory since it doesn't exist
			destDirectory.mkdir();
		}

		// Src needs to be a file
		if(srcTarOrGzFile.isFile() == false) {
			throw new IOException("Source tar is not a file.");
		}

		// Tar InputStream
		TarInputStream tInputStream = null;

		// File Extension (full name if none)
		String srcFilename = srcTarOrGzFile.getName().toLowerCase();
		String ext = srcFilename.substring((srcFilename.lastIndexOf('.') + 1), srcFilename.length());


		// Create tar input stream from a .tar.gz file or a normal .tar file
		if(ext.equalsIgnoreCase("gz") && srcFilename.contains("tar.gz")) {
			tInputStream = new TarInputStream( new GZIPInputStream( new FileInputStream(srcTarOrGzFile)));
		} else if(ext.equalsIgnoreCase("tar")) {
			
			tInputStream = new TarInputStream(new FileInputStream(srcTarOrGzFile));


		} else {
			throw new IOException("Invalid file extension. Supported: tar.gz, tar");
		}

		// Get the first entry in the archive
		
		TarEntry tarEntry = tInputStream.getNextEntry(); 
		while (tarEntry != null){  
			
			// Create a file with the same name as the tarEntry 
			File destPath = new File( destDirectory.getAbsolutePath() + File.separatorChar + tarEntry.getName());
			
			if(logger.isLoggable(Level.FINEST)) {
				logger.log(Level.FINEST, "Extracting " + destPath.getAbsolutePath());
			}
			
			if (tarEntry.isDirectory()){
				destPath.mkdirs();                           
			} else {
				
				// Grab the containing folder and if it doesn't exist, create it.
				if(destPath.getParentFile().exists() == false) {
					destPath.getParentFile().mkdirs();
				}
				
					
				FileOutputStream fOut = new FileOutputStream(destPath); 
				tInputStream.copyEntryContents(fOut);   
				fOut.close();                      
			}
			tarEntry = tInputStream.getNextEntry();
		}    
		tInputStream.close();
	}

	/**
	 * Tar's up a directory recursively
	 * @param srcDirectory
	 * @param destTarFile
	 * @throws IOException
	 */
	public static void createDirectoryTar(File srcDirectory, File destTarFile) throws IOException{
		
		if(destTarFile.getName().toLowerCase().endsWith(".tar") == false) {
			throw new IOException("Destination tar file is not a tar. " + destTarFile.getName().toLowerCase());
		}
		
		if(srcDirectory.exists() && srcDirectory.isDirectory() == false) {
			throw new IOException("Source directory is not a directory.");
		} else if(srcDirectory.exists() == false) {
			throw new IOException("Source directory does not exist.");
		}

		// We use this output stream to create the Tar file.  Make sure
		// this is set as LONGFILE_GNU -- so we support 8GB+ files and unlimited
		// length filenames.
		TarOutputStream tarOutputStream = new TarOutputStream(new FileOutputStream(destTarFile));         
		

		// Recurse through the directories
		recursiveTar(srcDirectory, srcDirectory, tarOutputStream);

		// Close our output stream, all done!
		tarOutputStream.close();

	}

	/**
	 * Private method which does the recursion and building of the tar file
	 * @param srcDir
	 * @param destTOS
	 * @throws IOException
	 */
	private static void recursiveTar(File rootDir, File curDir, TarOutputStream destTOS) throws IOException {

		if(curDir == null || rootDir == null || destTOS == null) {
			return;
		}
		
		File[] fList = curDir.listFiles();
		if(fList == null) {
			return;
		}
		
		byte[] buf = new byte[BUFFER_SIZE];

		int fListLen = fList.length;
		File file = null;
		for(int i = 0; i < fListLen; i++) {

			file = fList[i];

			if(file.canRead() == false) {
				
				System.out.println("Could not read file... ");
				if(file.getAbsolutePath() != null) {
					System.out.println("Unread File: " + file.getAbsolutePath());
				}
				continue;
			}
			
			// Directory? Recurse some more.
			if(file.isDirectory()) {

				recursiveTar(rootDir, file, destTOS);  

			} else {

				// File, let's add it to the Tar.

				String abs = rootDir.getAbsolutePath();
				String fileAbsPath = file.getAbsolutePath();
				
				
				// We need to set the file's absolute path starting above the root directory
				// Otherwise the tar will have useless folders in them.
				if(fileAbsPath.startsWith(abs)) {
					fileAbsPath = fileAbsPath.substring(abs.length()); 
				}
				
				
				if(logger.isLoggable(Level.FINEST)) {
					logger.log(Level.FINEST, "Adding File " + fileAbsPath);
				}

				FileInputStream fis = null;
				try {
					
					
					fis = new FileInputStream(file);
					TarEntry te = new TarEntry(fileAbsPath);
					te.setSize(file.length());
					destTOS.putNextEntry(te);
					int count = 0;
					while((count = fis.read(buf, 0, BUFFER_SIZE)) != -1) {
						destTOS.write(buf,0,count);    
					}

					

				} catch(IOException e) {
					throw e;
				} finally {

					// Close the Tar Output Stream...
					if(destTOS != null) {
						destTOS.closeEntry();
					}

					// Close the file input stream.
					if(fis != null) {
						fis.close();
					}
				}
			}
		}
	}

	/**
	 * Gzip an existing .tar file.  
	 * @param srcTarFile
	 * @param destTarGzFile
	 * @throws IOException
	 */
	public static void gzipTarFile(File srcTarFile, File destTarGzFile) throws IOException {

		FileInputStream inFile = null;
		GZIPOutputStream outGzipFile = null;

		if(srcTarFile.exists() == false) {
			throw new IOException("Source tar file does not exist.");
		}

		if(srcTarFile.getName().toLowerCase().endsWith(".tar") != false) {
			throw new IOException("Source tar file is not a tar.");
		}

		if(destTarGzFile.getName().toLowerCase().endsWith(".tar.gz") != false) {
			throw new IOException("Destination tar.gz file does not end with the proper extension.");
		}

		if(destTarGzFile.exists()) {
			throw new IOException("Destination tar.gz file already exists!");
		}

		try { 

			// Create the GZIP output stream 
			outGzipFile = new GZIPOutputStream(new FileOutputStream(destTarGzFile));

			// Open the input file 
			inFile = new FileInputStream(srcTarFile); 

			// Transfer bytes from the input file to the GZIP output stream 
			byte[] buf = new byte[1024]; 
			int len; 
			while ((len = inFile.read(buf)) > 0) { 
				outGzipFile.write(buf, 0, len); 
			}

			// Complete the GZIP file 
			outGzipFile.finish();

		} catch (IOException e) { 
			throw e;
		} finally {

			// Close all the files

			if(inFile != null) {
				inFile.close();
			}

			if(outGzipFile != null) {
				outGzipFile.close(); 
			}
		}
	}


}
