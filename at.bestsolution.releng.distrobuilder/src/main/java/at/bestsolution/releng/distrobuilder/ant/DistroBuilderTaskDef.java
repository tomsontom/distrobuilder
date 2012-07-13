package at.bestsolution.releng.distrobuilder.ant;

import org.apache.tools.ant.BuildException;

import at.bestsolution.releng.distrobuilder.DistroBuilder;
import at.bestsolution.releng.distrobuilder.InstallUnit;
import at.bestsolution.releng.distrobuilder.P2Repository;
import at.bestsolution.releng.distrobuilder.UpdateSite;

public class DistroBuilderTaskDef extends org.apache.tools.ant.Task {
	private DistroBuilder builder = new DistroBuilder();
	
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
	
	@Override
	public void execute() throws BuildException {
		builder.buildDistros();
//		System.err.println("HELLO WORLD!!!!");
	}
}
