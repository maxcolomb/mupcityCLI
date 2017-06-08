package org.thema.mupcity.task;

import java.io.File;

public class CompositeTask {
	public static void main(String[] args) throws Exception {
		String name = "dimFract";
		File folderIn = new File("/media/mcolomb/Data_2/resultExplo/dimFract/");
		double width = 28303;
		double height = 21019;
		double xmin = 914760;
		double ymin = 6680157;
		double shiftX = 0;
		double shiftY = 0;

		double minSize = 20;
		double maxSize = 5000;
		double seuilDensBuild = 0;

		int nMax = 5;
		boolean strict = true;
		double ahp8 = 0.083;
		double ahp7 = 0.083;
		double ahp6 = 0.083;
		double ahp5 = 0.04;
		double ahp4 = 0.218;
		double ahp3 = 0.218;
		double ahp2 = 0.218;
		double ahp1 = 0.03;
		double ahp0 = 0.027;

		boolean mean = true;

		long seed = 42;

		File filout = CompositeTask.run(name, folderIn, xmin, ymin, width, height, shiftX, shiftY, minSize, maxSize, seuilDensBuild, nMax, strict, ahp0, ahp1, ahp2, ahp3, ahp4, ahp5, ahp6, ahp7, ahp8, mean, seed);
		System.out.println(filout);
	}

	public static File run(String name, File folderIn, double xmin, double ymin, double width, double height, double shiftX, double shiftY, double minSize, double maxSize, double seuilDensBuild, int nMax, boolean strict, double ahp0, double ahp1, double ahp2, double ahp3, double ahp4, double ahp5, double ahp6, double ahp7, double ahp8, boolean mean, long seed) throws Exception {
		System.out.println("----------Project creation----------");
		File projectFile = ProjectCreationTask.run(name, folderIn, xmin, ymin, width, height, shiftX, shiftY);
		System.out.println("----------Decomp task----------");
		DecompTask.run(projectFile, name, minSize, maxSize, seuilDensBuild);
		System.out.println("----------Simulation task----------");
		SimulTask.run(projectFile, name, nMax, strict, ahp0, ahp1, ahp2, ahp3, ahp4, ahp5, ahp6, ahp7, ahp8, mean, seed);
		//RasterAnalyseTask.run(scenarFile, name);
		System.out.println("----------End task----------");
		return projectFile;
	}
}
