package at.bestsolution.releng.distrobuilder.ant;

import org.apache.tools.ant.BuildException;

import at.bestsolution.releng.distrobuilder.DistroBuilder;
import at.bestsolution.releng.distrobuilder.InstallUnit;
import at.bestsolution.releng.distrobuilder.P2Repository;
import at.bestsolution.releng.distrobuilder.UpdateSite;

public class DistroBuilderTaskDef extends org.apache.tools.ant.Task {
	private DistroBuilder builder = new DistroBuilder();
	private String appname = "distro";
	
	public InstallUnit createInstallUnit() {
		InstallUnit u = new InstallUnit();
		builder.addInstallUnit(u);
		return u;
	}
	
	public P2Repository createP2Repository() {
		P2Repository r = new P2Repository();
		builder.addP2Repository(r);
		return r;
	}
	
	public UpdateSite createUpdateSite() {
		UpdateSite u = new UpdateSite();
		builder.addUpdateSite(u);
		return u;
	}
	
	public String getTargetDirectory() {
		return builder.getBuildDirectory();
	}

	public void setTargetDirectory(String targetDirectory) {
		builder.setTargetDirectory(targetDirectory);
	}

	public String getP2DirectorExecutable() {
		return builder.getP2DirectorExecutable();
	}

	public void setP2DirectorExecutable(String p2DirectorExecutable) {
		builder.setP2DirectorExecutable(p2DirectorExecutable);
	}

	public String getStaticReposDirectory() {
		return builder.getStaticReposDirectory();
	}

	public void setStaticReposDirectory(String reposDirectory) {
		builder.setStaticReposDirectory(reposDirectory);
	}

	public String getBuildDirectory() {
		return builder.getBuildDirectory();
	}

	public void setBuildDirectory(String buildDirectory) {
		builder.setBuildDirectory(buildDirectory);
	}
	
	public String getDistDirectory() {
		return builder.getDistDirectory();
	}

	public void setDistDirectory(String distDirectory) {
		builder.setDistDirectory(distDirectory);
	}

	public String getVersion() {
		return builder.getVersion();
	}

	public void setVersion(String version) {
		builder.setVersion(version);
	}
	
	public String getAppname() {
		return appname;
	}
	
	public void setAppname(String appname) {
		this.appname = appname;
	}
	
	@Override
	public void execute() throws BuildException {
		try {
			builder.buildDistros(appname);
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
}
