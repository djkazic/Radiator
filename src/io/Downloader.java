package io;

import java.util.ArrayList;

import atrium.Core;
import atrium.NetHandler;
import atrium.Utilities;

public class Downloader implements Runnable {

	public static ArrayList<Downloader> downloaders = new ArrayList<Downloader> ();

	private BlockedFile blockedFile;
	private boolean download = true;

	public Downloader(BlockedFile bf) {
		blockedFile = bf;
		Utilities.log(this, "Downloader instance created for BlockedFile " + bf.getPointer().getName());
	}

	public void run() {
		try {
			if(downloaders.contains(this)) {
				return;
			} else {
				downloaders.add(this);
			}
			Utilities.log(this, "Enumerating block data blacklist");
			blockedFile.setBlackList(FileUtils.enumerateIncompleteBlackList(blockedFile));

			//Counter for length of blocks until we reach a quadrant of (~32MB)
			int quadrantMark = 0;

			String lastBlock = null;
			String block = null;

			Utilities.log(this, "Starting download");

			while(Core.peers.size() > 0 || !blockedFile.isComplete()) {
				if(!download) {
					Utilities.log(this, "Idling in pause");
					if(!Core.config.hubMode) {
						Core.mainWindow.updateTime(blockedFile.getChecksum(), "Paused");
					}
					Thread.sleep(3000);
					continue;
					//TODO: switch to CountDownLatch
				}

				block = blockedFile.nextBlockNeeded();

				if(block != null && !block.equals(lastBlock)) {
					lastBlock = block;
					Utilities.log(this, "Requesting block " + block);
					quadrantMark += Core.blockSize;
					NetHandler.requestBlock(blockedFile.getChecksum(), block);
					//long defaultWait = 100;
					//defaultWait *= (1 + ((blockedFile.getProgressNum() / 100D) * 1.5));
					//Utilities.log(this, "blockSleep: " + defaultWait);
					Thread.sleep(100);
				} else if(block != null && block.equals(lastBlock)) {
					Utilities.log(this, "Bad randomness, continue loop");
					Thread.sleep(5);
					continue;
				} else {
					if(blockedFile.getProgressNum() == 100) {
						Utilities.log(this, "BlockedFile " + blockedFile.getPointer().getName() + " is complete");
						blockedFile.setComplete(true);
						break;
					}
				}

				//If bytes counter is greater or equal to 32MB
				if(quadrantMark >= (32000 * 1000)) {
					Utilities.log(this, "Sleep for 32nd quadrant initiated");
					quadrantMark = 0;
					long defaultWait = 1000;
					defaultWait *= (0.8D + (blockedFile.getProgressNum() / 100D));
					Utilities.log(this, "Quadrant sleep: " + defaultWait);
					Thread.sleep(defaultWait);
				}
			}


			download = false;
			downloaders.remove(this);
			if(!Core.config.hubMode) {
				Utilities.log(this, "Assembling BlockedFile " + blockedFile.getPointer().getName());
				FileUtils.unifyBlocks(blockedFile);
			}
		} catch (Exception ex) {
			downloaders.remove(this);
			Utilities.log(this, "Downloader exception:");
			ex.printStackTrace();
		}
	}

	public static void pauseDownloader(String bfPointerStr) {
		for(Downloader dl : downloaders) {
			if(dl.blockedFile.getChecksum().equals(bfPointerStr)) {
				dl.download = false;
			}
		}
	}

	public static void resumeDownloader(String bfPointerStr) {
		for(Downloader dl : downloaders) {
			if(dl.blockedFile.getChecksum().equals(bfPointerStr)) {
				if(!dl.download) {
					if(!Core.config.hubMode) {
						Core.mainWindow.updateTime(dl.blockedFile.getChecksum(), " ... ");
					}
					dl.download = true;
				}
			}
		}
	}

	public static void removeDownloader(String bfPointerStr) {
		for(Downloader dl : downloaders) {
			if(dl.blockedFile.getChecksum().equals(bfPointerStr)) {
				if(!dl.download) {
					dl.download = false;
					downloaders.remove(dl);
				}
			}
		}
	}
}