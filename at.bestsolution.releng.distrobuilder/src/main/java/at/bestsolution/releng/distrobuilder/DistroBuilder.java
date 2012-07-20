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
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.tools.ant.taskdefs.condition.Os;
import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarInputStream;
import org.apache.tools.tar.TarOutputStream;
import org.apache.tools.zip.UnixStat;

public class DistroBuilder {
	private String targetDirectory;
	private String p2DirectorExecutable;
	private String staticReposDirectory;
	private String buildDirectory;
	private String distDirectory;
	private String version;

	private List<InstallUnit> iuList = new ArrayList<InstallUnit>();
	private List<UpdateSite> siteList = new ArrayList<UpdateSite>();
	private List<P2Repository> repoList = new ArrayList<P2Repository>();

	private static final int READ  = 1 << 2;
	private static final int WRITE = 1 << 1;
	private static final int EXEC  = 1;
	
	private static final int OWNER_EXEC = 00100;
	private static final int GROUP_EXEC = 00010;
	private static final int OTHER_EXEC = 00001;
	
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
	
	private void buildDistro(File targetSdksDir, String version, String os, String arch) {
		System.out.println("Build distro for " + version + " - " + os + " - " + arch);
		List<String> iuList = filterList(this.iuList, version, os, arch);
		List<String> repos = filterList(this.siteList, version, os, arch);
		repos.addAll(makeLocalRepos(new File(buildDirectory,"cache"), filterList(this.repoList, version, os, arch)));

		collectZipFiles(repos, staticReposDirectory, "shared", os, arch);
		collectZipFiles(repos, staticReposDirectory, version, os, arch);

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
					if( p.waitFor() == 0 ) {
						File distDir = new File(distDirectory);
						distDir.mkdirs();
						File out = new File(distDir, constructFilename(targetSdk.getName(),"efx",this.version));
						compress(rootDir, out);
					} else {
						System.err.println("Export failed");
					}
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
	
	private static void compress(File sourceDir, File targetFile) throws IOException {
		List<String> fileList = new ArrayList<String>();
		collectFiles(fileList, sourceDir,sourceDir.getName());
		
		if( targetFile.getName().endsWith(".zip") ) {
			targetFile.getParentFile().mkdirs();
			ZipOutputStream out = new ZipOutputStream(new FileOutputStream(targetFile));
			
			for( String f : fileList ) {
				ZipEntry e = new ZipEntry(f);
				out.putNextEntry(e);
				
				FileInputStream in = new FileInputStream(new File(sourceDir, f));
				byte[] buf = new byte[1024];
				int l;
				
				while( (l = in.read(buf)) != -1 ) {
					out.write(buf, 0, l);
				}
				in.close();
				out.closeEntry();
			}
			
			out.close();
		} else {
			targetFile.getParentFile().mkdirs();
			TarOutputStream out = new TarOutputStream(new GZIPOutputStream(new FileOutputStream(targetFile)));
			out.setLongFileMode(TarOutputStream.LONGFILE_GNU);
			
			for( String f : fileList ) {
				TarEntry e = new TarEntry(f);
				File tarFile = new File(sourceDir, f);
				if( tarFile.canExecute() ) {
					e.setMode(0755);
				}
				e.setSize(tarFile.length());
				out.putNextEntry(e);
				
				FileInputStream in = new FileInputStream(tarFile);
				byte[] buf = new byte[1024];
				int l;
				
				while( (l = in.read(buf)) != -1 ) {
					out.write(buf, 0, l);
				}
				in.close();
				out.closeEntry();
			}
			
			out.close();			
		}
	}
	
	private static void collectFiles(List<String> files, File dir, String prefix) {
		for( String f : dir.list() ) {
			File fd = new File(dir, f);
			if( fd.isDirectory() ) {
				collectFiles(files, fd, prefix.isEmpty() ? f : prefix+"/"+f);
			} else {
				files.add(prefix.isEmpty() ? f : prefix + "/" +f);
			}
		}
	}
	
	private static String constructFilename(String sourceName, String appDefinition, String version) {
		String suffix;
		if( sourceName.endsWith(".zip") ) {
			suffix = ".zip";
		} else {
			suffix = ".tar.gz";
		}
		
		return sourceName.substring(0,sourceName.length()-suffix.length()) + "-" + appDefinition + "-" + version + suffix;
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
						File f = new File(targetDirectory,e.getName());
						in.copyEntryContents(new FileOutputStream(f));
						
						int m = e.getMode();
						if( (m & OWNER_EXEC) == OWNER_EXEC 
								|| (m & GROUP_EXEC) == GROUP_EXEC 
								|| (m & OTHER_EXEC) == OTHER_EXEC ) {
							f.setExecutable(true, false);
						} else if( e.getLinkName() != null && e.getLinkName().trim().length() > 0 ) {
							//TODO Handle symlinks
//							System.err.println("A LINK: " + e.getLinkName());
						}
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
	
	
	
	public String getTargetDirectory() {
		return targetDirectory;
	}

	public void setTargetDirectory(String targetDirectory) {
		this.targetDirectory = targetDirectory;
	}

	public String getP2DirectorExecutable() {
		return p2DirectorExecutable;
	}

	public void setP2DirectorExecutable(String p2DirectorExecutable) {
		this.p2DirectorExecutable = p2DirectorExecutable;
	}

	public String getStaticReposDirectory() {
		return staticReposDirectory;
	}

	public void setStaticReposDirectory(String reposDirectory) {
		this.staticReposDirectory = reposDirectory;
	}

	public String getBuildDirectory() {
		return buildDirectory;
	}

	public void setBuildDirectory(String buildDirectory) {
		this.buildDirectory = buildDirectory;
	}

	public void addInstallUnit(InstallUnit unit) {
		this.iuList.add(unit);
	}
	
	public void addP2Repository(P2Repository repo) {
		this.repoList.add(repo);
	}
	
	public void addUpdateSite(UpdateSite site) {
		this.siteList.add(site);
	}
	
	public String getDistDirectory() {
		return distDirectory;
	}

	public void setDistDirectory(String distDirectory) {
		this.distDirectory = distDirectory;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
//		DistroBuilder b = new DistroBuilder();
//		b.setBuildDirectory("/tmp/jbuild");
//		b.setTargetDirectory("/Users/tomschindl/Desktop/efxclipse-all-in-one/jbuild/targets");
//		b.setP2DirectorExecutable("/Users/tomschindl/Desktop/efxclipse-all-in-one/jbuild/builder/eclipse");
//		b.setStaticReposDirectory("/Users/tomschindl/Desktop/efxclipse-all-in-one/jbuild/repos");
//		b.setDistDirectory("/tmp/jbuild/dist");
//		b.setVersion("0.0.14");
//		
//		b.addUpdateSite(new UpdateSite("http://cbes.javaforge.com/update", null, "win32", null));
//		
//		b.addP2Repository(new P2Repository("http://www.efxclipse.org/p2-repos/releases/at.bestsolution.efxclipse.tooling.updatesite-0.0.14.zip", null, null, null));
//		
//		b.addInstallUnit(new InstallUnit("org.eclipse.emf.sdk.feature.group,", null, null, null));
//		b.addInstallUnit(new InstallUnit("org.eclipse.xtext.sdk.feature.group", null, null, null));
//		b.addInstallUnit(new InstallUnit("org.eclipse.emf.mwe2.runtime.sdk.feature.group", null, null, null));
//		b.addInstallUnit(new InstallUnit("at.bestsolution.efxclipse.tooling.feature.feature.group", null, null, null));
//		b.addInstallUnit(new InstallUnit("at.bestsolution.efxclipse.runtime.e3.feature.feature.group", null, null, null));
//		b.addInstallUnit(new InstallUnit("org.eclipse.wst.xml_ui.feature.feature.group", null, null, null));
//		b.addInstallUnit(new InstallUnit("org.eclipse.egit.feature.group", null, null, null));
//		b.addInstallUnit(new InstallUnit("org.tigris.subversion.clientadapter.feature.feature.group", null, null, null));
//		b.addInstallUnit(new InstallUnit("org.tigris.subversion.subclipse.feature.group", null, null, null));
//		b.addInstallUnit(new InstallUnit("org.tigris.subversion.clientadapter.svnkit.feature.feature.group", null, null, null));
//		b.addInstallUnit(new InstallUnit("org.eclipse.e4.core.tools.feature.feature.group", null, null, null));
//		
//		b.addInstallUnit(new InstallUnit("org.tigris.subversion.clientadapter.javahl.feature.feature.group", null, "win32", "x86"));
//		b.addInstallUnit(new InstallUnit("mercurialeclipse.feature.group", null, "win32", null));
//		b.addInstallUnit(new InstallUnit("com.intland.hgbinary.win32.feature.group", null, "win32", null));
//		
//		b.buildDistros();
		
//		uncompress(new File("/Users/tomschindl/Desktop/efxclipse-all-in-one/e4/targetplatforms/eclipse-SDK-4.2RC2-macosx-cocoa-x86_64.tar.gz"), new File("/tmp/bla"));
//		try {
//			compress(new File("/tmp/bla"), new File("/tmp/bla.tar.gz"));
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//		
		
//		int read  = 1 << 2; // 4
//		int write = 1 << 1; // 2
//		int exec  = 1;      // 1
//		
//		int owner = read | write | exec;
//		int group = read | exec;
//		int other = read | exec;
//		
//		int a = Integer.parseInt("0"+owner+group+other, 8);
//		System.err.println((a & Integer.parseInt("00020", 8)) == Integer.parseInt("00020", 8));
//		
		
		
//		try {
//			compress(new File("/tmp/jbuild/tmp"), new File("/tmp/jbuild/dist/out.tar.gz"));
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
	}

}
