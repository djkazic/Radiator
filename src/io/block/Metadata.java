package io.block;

import java.io.File;
import java.io.FileOutputStream;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;

import atrium.Core;
import io.FileUtils;

public class Metadata {
	
	private String bfChecksum;
	private int ups;
	private int downs;
	private Map<String, Object[]> comments;
	private boolean voted;
	private long timestamp;

	public Metadata() {
		bfChecksum = null;
		ups = 0;
		downs = 0;
		comments = null;
		voted = false;
		timestamp = 0;
	}
	
	public Metadata(String bfChecksum) {
		this.bfChecksum = bfChecksum;
		comments = new HashMap<String, Object[]> ();
	}
	
	public boolean matchBf(String checksum) {
		return bfChecksum.equals(checksum);
	}
	
	public void addComment(String comment) {
		String signKey = Core.rsa.sign(comment);
		comments.put(comment, new Object[] {Core.rsa.rawPublicKey(), signKey});
		timestamp = System.currentTimeMillis();
	}
	
	public int getScore() {
		return ups - downs;
	}
	
	public ArrayList<String> getComments() {
		if(timestamp <= System.currentTimeMillis()) {
			ArrayList<String> approved = new ArrayList<String> ();
			for(Entry<String, Object[]> entry : comments.entrySet()) {
				String comment = (String) entry.getKey();
				Object[] pkAndSignature = (Object[]) entry.getValue();
				PublicKey pubKey = (PublicKey) pkAndSignature[0];
				String signature = (String) pkAndSignature[1];
				if(Core.rsa.verify(comment, pubKey, signature)) {
					approved.add(comment);
				}
			}
			return approved;
		}
		return null;
	}
	
	public void vote(int upDown) {
		if(!voted) {
			voted = true;
			if(upDown == 1) {
				ups++;
			} else if(upDown == -1) {
				downs++;
			}
		}
	}
	
	public String toString() {
		return "Checksum: " + bfChecksum + " | Score: " + getScore() + " | Comments: " + comments;
	}
	
	public static void serializeAll() {
		try {
			File metaDexSerialized = new File(FileUtils.getConfigDir() + "/metadex.dat");
			if(metaDexSerialized.exists()) {
				metaDexSerialized.delete();
			}
			
			if(Core.metaDex.size() > 0) {
				metaDexSerialized.createNewFile();
				Kryo kryo = new Kryo();
				FileOutputStream fos = new FileOutputStream(metaDexSerialized);
				Output out = new Output(fos);
				kryo.writeObject(out, Core.metaDex);
				out.close();
			}
		} catch (Exception e) { e.printStackTrace(); }
	}
}
