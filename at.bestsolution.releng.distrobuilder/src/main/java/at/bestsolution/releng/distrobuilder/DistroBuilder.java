package at.bestsolution.releng.distrobuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarInputStream;

public class DistroBuilder {
	private String targetDirectory;
	private String p2DirectorExecutable;
	private String reposDirectory;
	private String buildDirectory;

	private List<InstallUnit> iuList = new ArrayList<InstallUnit>();
	private List<UpdateSite> siteList = new ArrayList<UpdateSite>();
	private List<P2Repository> repositories = new ArrayList<P2Repository>();

	static class PipeThread extends Thread {
		private final InputStream in;
		private final PrintStream out;
		
		public PipeThread(InputStream in, PrintStream out) {
			setDaemon(true);
			this.in = in;
			this.out = out;
		}
		
		@Override
		public void run() {
			BufferedReader r = new BufferedReader(new InputStreamReader(in));
			String l;
			try {
				while( (l = r.readLine()) != null ) {
					out.println(l);
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	public DistroBuilder() {

	}

	private void buildDistro(File targetSdksDir, String version, String os, String arch) {
		System.out.println("Build distro for " + version + " - " + os + " - " + arch);
		List<String> iuList = filterList(this.iuList, version, os, arch);
		List<String> repos = filterList(this.siteList, version, os, arch);
		repos.addAll(makeLocalRepos(new File(buildDirectory,"cache"), filterList(this.repositories, version, os, arch)));

		collectZipFiles(repos, reposDirectory, "shared", os, arch);
		collectZipFiles(repos, reposDirectory, version, os, arch);

		String exec = p2DirectorExecutable + " -nosplash -application org.eclipse.equinox.p2.director -consoleLog -profileProperties org.eclipse.update.install.features=true -profile SDKProfile ";
		exec += " -installIU " + join(iuList,",");
		exec += " -repository " + join(repos,",") + " ";
		
		for (File targetSdk : targetSdksDir.listFiles()) {
			if (targetSdk.isFile()) {
				File f = new File(buildDirectory, "tmp");
				if( f.exists() ) {
					deleteDirectory(f);
				}
				
				File rootDir = uncompress(targetSdk, f);
				String commandString = exec + " -destination " + rootDir.getAbsolutePath();
				try {
					Process p = Runtime.getRuntime().exec(commandString);
					PipeThread stdThread = new PipeThread(p.getInputStream(), System.out);
					stdThread.start();
					PipeThread errThread = new PipeThread(p.getErrorStream(), System.err);
					errThread.start();
					System.out.println("====================> " + p.waitFor());
					stdThread.join();
					errThread.join();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
			}
		}
	}
	
	private static List<String> makeLocalRepos(File cacheDirectory, List<String> repositories) {
		List<String> rv = new ArrayList<String>();
		for( String repo : repositories ) {
			if( repo.startsWith("http://") ) {
				try {
					repo = downloadFile(new URL(repo), cacheDirectory).getAbsolutePath();
				} catch (MalformedURLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (NoSuchAlgorithmException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
			rv.add(toZipString(new File(repo)));
		}
		
		return rv;
	}
	
	private static File downloadFile(URL url, File cacheDirectory) throws IOException, NoSuchAlgorithmException {
		boolean download = true;
		MessageDigest d = MessageDigest.getInstance("MD5");
		d.update(url.toString().getBytes());
		String fileName = new BigInteger(1, d.digest()).toString(16) + ".zip";
		cacheDirectory.mkdirs();
		File f = new File(cacheDirectory, fileName);

		if (f.exists()) {
			HttpURLConnection.setFollowRedirects(false);
			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			con.setRequestMethod("HEAD");
			if (con.getResponseCode() == HttpURLConnection.HTTP_OK) {
				long lastmodified = con.getLastModified();
				download = f.lastModified() < lastmodified;
			}
		}

		if (download) {
			slurp(f, url);
		}
		return f;
	}
	
	private static boolean slurp(File targetFile, URL url) throws IOException {
		targetFile.delete();
		File f = new File(targetFile.getAbsolutePath()+".part");
		FileOutputStream out = new FileOutputStream(f);
		
		InputStream in = url.openStream();
		byte[] buf = new byte[1024];
		int l;
		while( (l = in.read(buf)) != -1 ) {
			out.write(buf, 0, l);
		}
		out.close();
		return f.renameTo(targetFile);
	}
	
	private static List<String> filterList(List<? extends FilteredElement> list, String version, String os, String arch) {
		List<String> rv = new ArrayList<String>();
		
		for( FilteredElement u : list ) {
			if( u.getVersion() != null && ! u.getVersion().equals(version) ) {
				continue;
			}
			
			if( u.getOs() != null && ! u.getOs().equals(os) ) {
				continue;
			}
			
			if( u.getArch() != null && ! u.getArch().equals(arch) ) {
				continue;
			}
			
			rv.add(u.getValue());
		}
		
		return rv;
	}

	private static File uncompress(File compressedFile, File targetDirectory) {
		File targetDir = null;
		if( compressedFile.getName().endsWith(".tar.gz") ) {
			try {
				TarInputStream in = new TarInputStream(new GZIPInputStream(new FileInputStream(compressedFile)));
				TarEntry e;
				while( (e = in.getNextEntry()) != null ) {
					if( e.isDirectory() ) {
						File f = new File(targetDirectory,e.getName());
						f.mkdirs();
						if( targetDir == null ) {
							targetDir = f;
						}
					} else {
						in.copyEntryContents(new FileOutputStream(new File(targetDirectory,e.getName())));
					}
				}
				in.close();
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		} else if( compressedFile.getName().endsWith(".zip") ) {
			try {
				ZipInputStream in = new ZipInputStream(new FileInputStream(compressedFile));
				ZipEntry e;
				while( (e = in.getNextEntry()) != null ) {
					if( e.isDirectory() ) {
						File f = new File(targetDirectory,e.getName());
						f.mkdirs();
						if( targetDir == null ) {
							targetDir = f;
						}
					} else {
						FileOutputStream out = new FileOutputStream(new File(targetDirectory,e.getName()));
						byte[] buf = new byte[1024];
						int l;
						while( (l = in.read(buf, 0, 1024)) != -1 ) {
							out.write(buf,0,l);
						}
						out.close();
					}
					in.closeEntry();
				}
				in.close();
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}
		return targetDir;
	}
	
	private static boolean deleteDirectory(File path) {
		if (path.exists()) {
			File[] files = path.listFiles();
			for (File f : files) {
				if (f.isDirectory()) {
					deleteDirectory(f);
				} else {
					f.delete();
				}
			}
		}
		return (path.delete());
	}

	private static String join(List<String> entries, String sep) {
		StringBuilder b = new StringBuilder();
		for (String entry : entries) {
			if (b.length() > 0) {
				b.append(sep);
			}
			b.append(entry);
		}
		return b.toString();
	}

	private static void collectZipFiles(List<String> collectedZips, String rootDir, String version, String os, String arch) {
		File shared = new File(rootDir, version);
		for (File f : shared.listFiles()) {
			if (f.isDirectory()) {
				if (f.getName().equals(os)) {
					for (File fOs : f.listFiles()) {
						if (fOs.isDirectory()) {
							if (fOs.getName().equals(arch)) {
								for (File fArch : fOs.listFiles()) {
									if (fArch.isFile() && fArch.getName().endsWith(".zip")) {
										collectedZips.add(toZipString(fArch));
									}
								}
							}
						} else if (fOs.isFile() && fOs.getName().endsWith(".zip")) {
							collectedZips.add(toZipString(fOs));
						}
					}
				}
			}
			if (f.isFile() && f.getName().endsWith(".zip")) {
				collectedZips.add(toZipString(f));
			}
		}
	}

	private static String toZipString(File f) {
		return "jar:file:" + f.getParentFile().getAbsolutePath() + "/" + f.getName() + "!/";
	}
	
	public void buildDistros() {
		File f = new File(targetDirectory);
		for (File versionDir : f.listFiles()) {
			if (versionDir.isDirectory()) {
				for (File osDir : versionDir.listFiles()) {
					if (osDir.isDirectory()) {
						for (File archDir : osDir.listFiles()) {
							if (archDir.isDirectory()) {
								buildDistro(archDir, versionDir.getName(), osDir.getName(), archDir.getName());
							}
						}
					}
				}
			}
		}
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		DistroBuilder b = new DistroBuilder();
		b.buildDirectory = "/tmp/jbuild";
		b.targetDirectory = "/Users/tomschindl/Desktop/efxclipse-all-in-one/jbuild/targets";
		b.p2DirectorExecutable = "/Users/tomschindl/Desktop/efxclipse-all-in-one/jbuild/builder/eclipse";
		b.reposDirectory = "/Users/tomschindl/Desktop/efxclipse-all-in-one/jbuild/repos";
		
		b.siteList.add(new UpdateSite("http://cbes.javaforge.com/update", null, "win32", null));
		
		b.repositories.add(new P2Repository("http://eos.bestsolution.at:8080/job/e(fx)clipse/4/at.bestsolution.efxclipse$at.bestsolution.efxclipse.tooling.updatesite/artifact/at.bestsolution.efxclipse/at.bestsolution.efxclipse.tooling.updatesite/0.1.0-SNAPSHOT/at.bestsolution.efxclipse.tooling.updatesite-0.1.0-SNAPSHOT-assembly.zip", null, null, null));
		
		b.iuList.add(new InstallUnit("org.eclipse.emf.sdk.feature.group,", null, null, null));
		b.iuList.add(new InstallUnit("org.eclipse.xtext.sdk.feature.group", null, null, null));
		b.iuList.add(new InstallUnit("org.eclipse.emf.mwe2.runtime.sdk.feature.group", null, null, null));
		b.iuList.add(new InstallUnit("at.bestsolution.efxclipse.tooling.feature.feature.group", null, null, null));
		b.iuList.add(new InstallUnit("at.bestsolution.efxclipse.runtime.e3.feature.feature.group", null, null, null));
		b.iuList.add(new InstallUnit("org.eclipse.wst.xml_ui.feature.feature.group", null, null, null));
		b.iuList.add(new InstallUnit("org.eclipse.egit.feature.group", null, null, null));
		b.iuList.add(new InstallUnit("org.tigris.subversion.clientadapter.feature.feature.group", null, null, null));
		b.iuList.add(new InstallUnit("org.tigris.subversion.subclipse.feature.group", null, null, null));
		b.iuList.add(new InstallUnit("org.tigris.subversion.clientadapter.svnkit.feature.feature.group", null, null, null));
		b.iuList.add(new InstallUnit("org.eclipse.e4.core.tools.feature.feature.group", null, null, null));
		
		b.iuList.add(new InstallUnit("org.tigris.subversion.clientadapter.javahl.feature.feature.group", null, "win32", "x86"));
		b.iuList.add(new InstallUnit("mercurialeclipse.feature.group", null, "win32", null));
		b.iuList.add(new InstallUnit("com.intland.hgbinary.win32.feature.group", null, "win32", null));
		
		b.buildDistros();
//		try {
//			downloadFile(new URL("http://eos.bestsolution.at:8080/job/e(fx)clipse/4/at.bestsolution.efxclipse$at.bestsolution.efxclipse.tooling.updatesite/artifact/at.bestsolution.efxclipse/at.bestsolution.efxclipse.tooling.updatesite/0.1.0-SNAPSHOT/at.bestsolution.efxclipse.tooling.updatesite-0.1.0-SNAPSHOT-assembly.zip"), new File("/tmp/jbuild/cache"));
//			
//		} catch (MalformedURLException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
	}

}
