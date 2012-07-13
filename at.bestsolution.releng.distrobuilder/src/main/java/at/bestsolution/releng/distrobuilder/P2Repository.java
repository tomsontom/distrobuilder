package at.bestsolution.releng.distrobuilder;

public class P2Repository implements FilteredElement {
	private String url;
	private String version;
	private String os;
	private String arch;

	public P2Repository() {
	}

	public P2Repository(String url, String version, String os, String arch) {
		this.url = url;
		this.version = version;
		this.os = os;
		this.arch = arch;
	}
	
	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public String getOs() {
		return os;
	}

	public void setOs(String os) {
		this.os = os;
	}

	public String getArch() {
		return arch;
	}

	public void setArch(String arch) {
		this.arch = arch;
	}

	@Override
	public String getValue() {
		return getUrl();
	}
	
	
}
