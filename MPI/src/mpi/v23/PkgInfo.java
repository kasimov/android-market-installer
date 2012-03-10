package mpi.v23;


public class PkgInfo {
	static int iseq = 0;
	
	String apkfile;
	String pkgname;
	int    numtries;
	int    waiting;
	int    maxtries;
	int 	seq;
	long	lastInstall;
	
	PkgInfo(String apkPath, int max){
		apkfile  = apkPath;
		waiting  = 0;
		numtries = 0;
		seq = iseq++;
		// maxtries = max;
		maxtries = 1;
	}
	
	PkgInfo(String apkPath) {
		this(apkPath, 1);
	}
	
	public static int seq_start(){
		return ++iseq;
	}
	
	public static void seq_end() {
		
	}
}
