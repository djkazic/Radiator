package io;

import java.awt.Desktop;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;

import javax.swing.filechooser.FileSystemView;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;

import atrium.Core;
import atrium.Utilities;
import filter.FilterUtils;
import io.block.BlockedFile;
import io.serialize.SerialBlockedFile;

public class FileUtils {

	public static Comparator<BlockedFile> bfComparator;
	private static ArrayList<File> filterPassed;

	public static void initDirs() {
		try {
			Utilities.log("FileUtils", "Initializing file worker", false);
			File findir = new File(getWorkspaceDir());
			if (!findir.exists()) {
				Utilities.log("FileUtils", "Could not find directory, creating", false);
				boolean attempt = false;
				try {
					findir.mkdir();
					attempt = true;
				} catch (SecurityException se) {
					se.printStackTrace();
				}
				if (attempt) {
					Utilities.log("FileUtils", "Successfully created directory", false);
				}
			} else {
				Utilities.log("FileUtils", "Found workspace directory", false);
			}
			File configDir = new File(getConfigDir());
			if (!configDir.exists()) {
				Utilities.log("FileUtils", "Could not find config directory, creating", false);
				boolean attempt = false;
				try {
					configDir.mkdir();
					attempt = true;
				} catch (SecurityException se) {
					se.printStackTrace();
				}
				if (attempt) {
					Utilities.log("FileUtils", "Successfully created config directory", false);
				}
			} else {
				Utilities.log("FileUtils", "Found config directory", false);
			}
			File appDataGen = new File(getAppDataDir());
			if (!appDataGen.exists()) {
				Utilities.log("FileUtils", "Could not find appData directory, creating", false);
				boolean attempt = false;
				try {
					appDataGen.mkdir();
					attempt = true;
				} catch (Exception e) {
					e.printStackTrace();
				}
				if (attempt) {
					Utilities.log("FileUtils", "Successfully created appData directory", false);
				}
			} else {
				Utilities.log("FileUtils", "Found data directory", false);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public static String getWorkspaceDir() {
		String directory = null;
		try {
			directory = FileSystemView.getFileSystemView().getDefaultDirectory().getPath().toString();
			if (Utilities.isMac()) {
				directory += "/Documents/Gemini";
			} else {
				directory += "/Gemini";
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return directory;
	}

	public static String getAppDataDir() {
		String scratchDirectory;
		if (Utilities.isWindows()) {
			scratchDirectory = System.getenv("AppData") + "/Gemini";
		} else {
			scratchDirectory = getWorkspaceDir() + "/.cache";
		}
		return scratchDirectory;
	}

	public static String getConfigDir() {
		return getWorkspaceDir() + "/.config";
	}

	public static File findBlockAppData(BlockedFile foundBlock, String block) {
		File directory = new File(foundBlock.getBlocksFolder());
		if (!directory.exists()) {
			Utilities.log("FileUtils",
					"Data directory for origin " + foundBlock.getPointer().getName() + " is not initialized", true);
			return null;
		}
		File[] listOfFiles = directory.listFiles();
		if (listOfFiles != null && listOfFiles.length > 0) {
			for (int i = 0; i < listOfFiles.length; i++) {
				try {
					// TODO: server-sided check for hubMode cached block (temp
					// files, check)
					if (!Core.config.hubMode) {
						if (generateChecksum(listOfFiles[i]).equals(block)) {
							return listOfFiles[i];
						} else {
							Utilities.log("FileUtils", "Checksum mismatch for block", true);
							return null;
						}
					} else {
						if (listOfFiles[i].getName().equals(block)) {
							return listOfFiles[i];
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		return null;
	}

	public static byte[] findBlockFromComplete(BlockedFile bf, int blockIndex) {
		try {
			int res = 0;
			try {
				byte[] rafBuffer = new byte[Core.blockSize];
				RandomAccessFile raf = new RandomAccessFile(bf.getPointer(), "r");
				raf.seek(Core.blockSize * (blockIndex));
				res = raf.read(rafBuffer);
				raf.close();
				if (res != rafBuffer.length) {
					byte[] smallChunk = new byte[res];
					System.arraycopy(rafBuffer, 0, smallChunk, 0, res);
					return smallChunk;
				}
				return rafBuffer;
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return null;
	}

	public static void genBlockIndex() {
		try {
			File encCacheFile = new File(getConfigDir() + "/eblockdex.dat");
			File cacheFile = new File(getConfigDir() + "/blockdex.dat");
			if (encCacheFile.exists()) {
				Utilities.log("FileUtils", "Attempting to read blockdex cache", false);
				try {
					byte[] encFileBytes = Files.readAllBytes(encCacheFile.toPath());
					byte[] decryptedBytes = Core.aes.decrypt(encFileBytes);
					FileOutputStream fos = new FileOutputStream(cacheFile);
					fos.write(decryptedBytes);
					fos.close();
					encCacheFile.delete();
					Kryo kryo = new Kryo();
					Input input = new Input(new FileInputStream(cacheFile));

					try {
						ArrayList<?> uKbf = kryo.readObject(input, ArrayList.class);
						Utilities.log("FileUtils", "Read " + uKbf.size() + " entries from cache", true);
						for (int i = 0; i < uKbf.size(); i++) {
							((SerialBlockedFile) uKbf.get(i)).toBlockedFile();
						}
					} catch (Exception ex) {
						ex.printStackTrace();
					}
					input.close();
					cacheFile.delete();
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}

			int actualBfCount = 0;
			File baseFolder = new File(getWorkspaceDir());

			if (Core.config.hubMode) {
				if (baseFolder != null && baseFolder.listFiles().length > 0) {
					File[] files = baseFolder.listFiles();
					int counter = 0;
					if (files != null) {
						for (File file : files) {
							if (!file.getName().startsWith(".")) {
								FileUtils.deleteRecursive(file);
								counter++;
							}
						}
						if (counter > 0) {
							Utilities.log("atriuum.FileUtils",
									"Hub-mode violation: files in workpace. Clearing workspace.", false);
						}
					}
				}
				File appData = new File(FileUtils.getAppDataDir());
				if (appData.exists()) {
					Utilities.log("FileUtils", "Examining app data directory", false);
					File[] blockedFileFolders = appData.listFiles();
					if (blockedFileFolders != null && blockedFileFolders.length > 0) {
						ArrayList<String> appDataDirectories = new ArrayList<String>();
						for (File file : blockedFileFolders) {
							if (file.isDirectory() && !file.getName().startsWith(".")) {
								appDataDirectories.add(file.getName());
							}
						}
						actualBfCount = appDataDirectories.size();

						while (actualBfCount != Core.blockDex.size()
								&& Core.blockDex.size() > appDataDirectories.size()) {
							for (int i = 0; i < Core.blockDex.size(); i++) {
								BlockedFile bf = Core.blockDex.get(i);
								if (!appDataDirectories.contains(bf.getChecksum())) {
									Core.blockDex.remove(bf);
									i--;
								}
							}
							BlockedFile.serializeAll();
						}

						while (appDataDirectories.size() != Core.blockDex.size()
								&& Core.blockDex.size() < appDataDirectories.size()) {
							// No cache, dump everything not in the blockDex
							for (File file : blockedFileFolders) {
								boolean foundInDex = false;
								for (BlockedFile bf : Core.blockDex) {
									if (bf.getChecksum().equals(file.getName())) {
										foundInDex = true;
										break;
									}
								}
								if (!foundInDex) {
									FileUtils.deleteRecursive(file);
									appDataDirectories.remove(file.getName());
								}
							}
							actualBfCount = appDataDirectories.size();
						}
					}
				}
			} else {
				filterPassed = enumerateBasefolder();
				actualBfCount = filterPassed.size();

				while (Core.blockDex.size() != actualBfCount && Core.blockDex.size() < actualBfCount) {
					Utilities.log("FileUtils",
							"Validity check FAIL, cached " + Core.blockDex.size() + " but detected " + actualBfCount,
							false);
					for (int i = 0; i < filterPassed.size(); i++) {
						if (!filterPassed.get(i).getName().startsWith(".")
								&& (getBlockedFile(generateChecksum(filterPassed.get(i))) == null)) {
							if (FilterUtils.mandatoryFilter(filterPassed.get(i).getName())) {
								new BlockedFile(filterPassed.get(i), true);
							}
						}
					}
					BlockedFile.serializeAll();
				}

				while (Core.blockDex.size() != actualBfCount && Core.blockDex.size() > actualBfCount) {
					Utilities.log("FileUtils",
							"Validity check FAIL, cached " + Core.blockDex.size() + " but detected " + actualBfCount,
							false);
					for (int i = 0; i < Core.blockDex.size(); i++) {
						BlockedFile curBf = Core.blockDex.get(i);
						File curPointer = curBf.getPointer();
						boolean found = false;
						for (int j = 0; j < filterPassed.size(); j++) {
							if (curPointer.equals(filterPassed.get(j))) {
								found = true;
								break;
							}
						}
						if (!found) {
							Core.blockDex.remove(curBf);
						}
					}
					BlockedFile.serializeAll();
				}
			}

			String checkPassFail = (Core.blockDex.size() == actualBfCount) ? "PASS" : "FAIL";
			Utilities.log("FileUtils", "Final validity check: " + checkPassFail + "; cached " + Core.blockDex.size()
					+ " and detected " + actualBfCount, false);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public static ArrayList<File> enumerateBasefolder() {
		ArrayList<File> output = new ArrayList<File>();
		File baseFolder = new File(getWorkspaceDir());
		if (baseFolder != null && baseFolder.listFiles().length > 0) {
			File[] list = baseFolder.listFiles();
			if (list != null && list.length > 0) {
				for (int i = 0; i < list.length; i++) {
					if (!list[i].getName().startsWith(".")) {
						if (list[i].isFile()) {
							if (FilterUtils.mandatoryFilter(list[i].getName())) {
								output.add(list[i]);
							} else {
								list[i].delete();
							}
						} else {
							try {
								processDir(list[i]);
							} catch (Exception ex) {
								ex.printStackTrace();
							}
						}
					}
				}
			}
		}
		return output;

	}

	private static void processDir(File preDef) throws InterruptedException, IOException {
		final File bfs = preDef;

		if (bfs.isDirectory()) {
			FileVisitor<Path> fv = new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes atts) throws IOException {
					Utilities.log(this, "Visiting file " + file.getFileName(), true);
					try {
						processDir(file.toFile());
					} catch (Exception ex) {
						ex.printStackTrace();
					}
					return FileVisitResult.CONTINUE;
				}
			};

			try {
				Files.walkFileTree(bfs.toPath(), fv);
			} catch (IOException e) {
				// Second to last exception
			}
		} else {
			if (FilterUtils.mandatoryFilter(bfs.getName())) {
				filterPassed.add(bfs);
			} else {
				FileUtils.deleteRecursive(bfs);
				FileUtils.removeFileAndParentsIfEmpty(bfs.toPath());
			}
		}
	}

	public static boolean haveInBlockDex(File file) {
		for (BlockedFile bf : Core.blockDex) {
			if (bf.getPointer().equals(file)) {
				return true;
			}
		}
		return false;
	}

	public static String generateChecksum(File file) {
		try {
			InputStream fis = new FileInputStream(file);
			byte[] buffer = new byte[8096];
			MessageDigest complete = MessageDigest.getInstance("SHA1");
			int numRead;
			do {
				numRead = fis.read(buffer);
				if (numRead > 0) {
					complete.update(buffer, 0, numRead);
				}
			} while (numRead != -1);
			fis.close();
			String result = "";
			byte[] digest = complete.digest();
			for (int i = 0; i < digest.length; i++) {
				result += Integer.toString((digest[i] & 0xff) + 0x100, 16).substring(1);
			}
			return result;
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return null;
	}

	public static ArrayList<String> enumerateBlocks(BlockedFile bf, boolean hubMode) {
		try {
			File file = bf.getPointer();
			ArrayList<String> blockList = new ArrayList<String>();
			InputStream fis = new FileInputStream(file);
			byte[] buffer = new byte[Core.blockSize];

			MessageDigest complete = MessageDigest.getInstance("SHA1");
			int numRead;
			do {
				numRead = fis.read(buffer);
				if (numRead > 0) {
					complete.update(buffer, 0, numRead);
					if (hubMode) {
						String result = "";
						byte[] digest = complete.digest();
						for (int i = 0; i < digest.length; i++) {
							result += Integer.toString((digest[i] & 0xff) + 0x100, 16).substring(1);
						}
						blockList.add(result);
						File blockFolder = new File(bf.getBlocksFolder());
						if (!blockFolder.exists()) {
							blockFolder.mkdir();
						}
						File thisBlock = new File(bf.getBlocksFolder() + "/" + result);
						if (!thisBlock.exists()) {
							thisBlock.createNewFile();
							FileOutputStream fos = new FileOutputStream(thisBlock, true);
							byte[] acc = new byte[numRead];
							for (int i = 0; i < acc.length; i++) {
								acc[i] = buffer[i];
							}
							fos.write(Core.aes.encrypt(acc));
							fos.close();
						}
					}
				} else {
					break;
				}
				if (!Core.config.hubMode) {
					byte[] digest = complete.digest();
					String result = "";
					for (int i = 0; i < digest.length; i++) {
						result += Integer.toString((digest[i] & 0xff) + 0x100, 16).substring(1);
					}
					blockList.add(result);
				}
			} while (numRead > 0);
			fis.close();

			// Delete file after physical blocking if running in hubMode
			if (Core.config.hubMode) {
				Utilities.log("FileUtils", "Removing base import file", false);
				FileUtils.deleteRecursive(bf.getPointer());
				FileUtils.removeFileAndParentsIfEmpty(bf.getPointer().toPath());
			}
			return blockList;
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return null;
	}

	public static ArrayList<String> enumerateIncompleteBlackList(BlockedFile bf) {
		try {
			ArrayList<String> output = new ArrayList<String>();
			File bfDir = new File(bf.getBlocksFolder());
			if (bfDir.exists()) {
				File[] files = bfDir.listFiles();
				if (files != null && files.length > 0) {
					for (File file : files) {
						if (FileUtils.generateChecksum(file).equals(file.getName())) {
							output.add(file.getName());
						}
					}
				}
			}
			return output;
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return null;
	}

	public static void unifyBlocksStream(BlockedFile bf) throws Exception {
		String outputPath = bf.getStreamPath();
		BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(outputPath));

		for (String block : bf.getBlockList()) {
			File thisBlockFile = new File(bf.getBlocksFolder() + "/" + block);
			BufferedInputStream in = new BufferedInputStream(new FileInputStream(thisBlockFile));
			int pointer;
			while ((pointer = in.read()) != -1) {
				out.write(pointer);
			}
			in.close();
		}

		out.close();

		File blocksDir = new File(bf.getBlocksFolder());
		File[] blocksDirBlocks = blocksDir.listFiles();
		if (blocksDirBlocks != null && blocksDirBlocks.length > 0) {
			for (File file : blocksDirBlocks) {
				file.delete();
			}
		}
	}

	public static void unifyBlocks(BlockedFile bf) throws Exception {
		int numberParts = bf.getBlockList().size();
		String outputPath = bf.getPath();
		BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(outputPath));
		File[] blocks = new File(bf.getBlocksFolder()).listFiles();
		if (blocks != null && blocks.length > 0) {
			if (blocks.length != numberParts) {
				Utilities.log("FileUtils",
						"Number of blocks present (" + blocks.length + ") != number of parts (" + numberParts + ")",
						false);
				out.close();
				return;
			}
		}
		for (String block : bf.getBlockList()) {
			File thisBlockFile = new File(bf.getBlocksFolder() + "/" + block);
			BufferedInputStream in = new BufferedInputStream(new FileInputStream(thisBlockFile));
			int pointer;
			while ((pointer = in.read()) != -1) {
				out.write(pointer);
			}
			in.close();
		}
		out.close();
		// Clear haveList, so progressBar doesn't show 200%
		bf.getBlacklist().clear();
		// Reset progress
		bf.setProgress("0%");
		// Delete contents then the block directory
		File blocksDir = new File(bf.getBlocksFolder());
		File[] blocksDirBlocks = blocksDir.listFiles();
		if (blocksDirBlocks != null && blocksDirBlocks.length > 0) {
			for (File file : blocksDirBlocks) {
				file.delete();
			}
		}
		blocksDir.delete();
		if (blocksDir.exists()) {
			Utilities.log("FileUtils", "Unable to clear data for " + bf.getPointer().getName(), false);
		}
		// Set complete flag
		bf.setComplete(true);
	}

	public static void openBlockedFile(BlockedFile bf) {
		if (bf.isComplete()) {
			Desktop thisDesktop = Desktop.getDesktop();
			try {
				thisDesktop.open(bf.getPointer());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public static BlockedFile getBlockedFile(String checksum) {
		for (int i=0; i < Core.blockDex.size(); i++) {
			BlockedFile block = Core.blockDex.get(i);
			if (block.getChecksum().equals(checksum)) {
				return block;
			}
		}
		return null;
	}

	public static BlockedFile getBlockedFile(ArrayList<String> blockList) {
		for (BlockedFile block : Core.blockDex) {
			if (block.getBlockList().containsAll(blockList) && blockList.containsAll(block.getBlockList())) {
				return block;
			}
		}
		return null;
	}

	public static void copyStream(InputStream is, OutputStream os) throws IOException {
		int i;
		byte[] b = new byte[1024];
		while ((i = is.read(b)) != -1) {
			os.write(b, 0, i);
		}
	}

	public static boolean deleteRecursive(File path) {
		try {
			boolean ret = true;
			if (path != null) {
				if (path.isDirectory()) {
					File[] files = null;
					if ((files = path.listFiles()) != null && files.length > 0) {
						for (File file : files) {
							ret = ret && FileUtils.deleteRecursive(file);
						}
					}
				}
				return ret && path.delete();
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return false;
	}

	public static void removeFileAndParentsIfEmpty(Path path) throws IOException {
		if (path == null || path.equals(new File(FileUtils.getWorkspaceDir()).toPath())) {
			return;
		}

		if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
			Files.deleteIfExists(path);
		} else if (Files.isDirectory(path)) {
			try {
				Files.delete(path);
			} catch (Exception ex) {
				FileUtils.deleteRecursive(path.toFile());
				return;
			}
		}
		removeFileAndParentsIfEmpty(path.getParent());
	}

	public static boolean cacheReady(BlockedFile intermediate) {
		if (Core.config.hubMode) {
			return true;
		} else {
			long estimatedAddition = intermediate.getBlockList().size() * Core.blockSize;
			long nonCache = 0;
			long cached = 0;

			for (BlockedFile bf : Core.blockDex) {
				if (bf.getCacheStatus()) {
					cached += (bf.getBlacklist().size() * Core.blockSize);
				} else {
					nonCache += bf.getPointer().length();
				}
			}

			if (nonCache == 0) {
				Utilities.log("FileUtils", "No downloaded files yet, stopping cachepull", false);
			}
			return ((cached + estimatedAddition) <= nonCache);
		}
	}

	public static String getExtension(String input) {
		int ind = input.lastIndexOf(".");
		if (ind > 0) {
			String extension = input.substring(ind + 1);
			return extension.toLowerCase();
		} else {
			Utilities.log("filter.FilterUtils", "Rejected [" + input + "]", true);
			return null;
		}
	}
	
	public static String removeExtension(String str) {
	    String separator = System.getProperty("file.separator");
	    String searchSploitFileName;

	    // Remove the path upto the searchSploitFileName.
	    int lastSeparatorIndex = str.lastIndexOf(separator);
	    if (lastSeparatorIndex == -1) {
	        searchSploitFileName = str;
	    } else {
	        searchSploitFileName = str.substring(lastSeparatorIndex + 1);
	    }

	    // Remove the extension.
	    int extensionIndex = searchSploitFileName.lastIndexOf(".");
	    if (extensionIndex == -1)
	        return searchSploitFileName;

	    return searchSploitFileName.substring(0, extensionIndex);
	}
}
