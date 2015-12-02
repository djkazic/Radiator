package io;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;
import atrium.Core;
import atrium.Utilities;
import io.serialize.BlockdexSerializer;

/**
 * Watches file in working directory, and adds/removes them as BlockedFiles live
 * @author Kevin Cai
 */
public class FileWatcher implements Runnable {
	
	private WatchKey watchKey;
	
	public FileWatcher() {
		Path regDir = Paths.get(FileUtils.getWorkspaceDir());
		try {
			WatchService fileWatcher = regDir.getFileSystem().newWatchService();
			watchKey = regDir.register(fileWatcher, 
									   StandardWatchEventKinds.ENTRY_CREATE, 
									   StandardWatchEventKinds.ENTRY_DELETE);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		Utilities.log(this, "FileWatcher registered to normal directory");
	}
	
	/**
	 * Main FileWatcher loop; examines the workspace directory for changes
	 */
	public void run() {
		while(true) {
			List<WatchEvent<?>> events = watchKey.pollEvents();
			for(WatchEvent<?> we : events) {
				if(we.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
					Utilities.log(this, "Created: " + we.context().toString());
					new BlockedFile(new File(FileUtils.getWorkspaceDir() + "/" + we.context().toString()), true);
					if(!Core.headless) {
						Core.mainWindow.updateLibrary();
					}
				}
				if(we.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
					Utilities.log(this, "Deleted: " + we.context().toString());
					BlockedFile bf = FileUtils.getBlockedFile(we.context().toString());
					if(bf != null) {
						Core.blockDex.remove(bf);
						BlockdexSerializer.run();
						if(!Core.headless) {
							Core.mainWindow.updateLibrary();
						}
					}
				}
			}
			try {
				Thread.sleep(300);
			} catch (InterruptedException ex) {
				ex.printStackTrace();
			}
		}
	}
}