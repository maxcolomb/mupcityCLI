/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.thema.mupcity.rule;

import com.vividsolutions.jts.geom.*;
import java.awt.image.DataBuffer;
import java.util.*;
import org.thema.mupcity.Project;
import org.thema.mupcity.Project.Layers;
import org.thema.common.fuzzy.DiscreteFunction;
import org.thema.common.fuzzy.MembershipFunction;
import org.thema.common.parallel.BufferTask;
import org.thema.common.param.ReflectObject;
import org.thema.data.feature.AbstractFeature;
import org.thema.data.feature.DefaultFeature;
import org.thema.data.feature.DefaultFeatureCoverage;
import org.thema.data.feature.Feature;
import org.thema.msca.Cell;
import org.thema.msca.operation.AbstractLayerOperation;

/**
 *
 * @author gvuidel
 */
public class Facility12Rule extends AbstractRule {

    @ReflectObject.NoParam
    int level;
    
    double maxDistClust;
    
    @ReflectObject.Name("Distance max between facility")
    double distClust = 200;
    
    @ReflectObject.Name("Diversity function")
    MembershipFunction diversity;
    @ReflectObject.Name("Count function")
    MembershipFunction count;
    @ReflectObject.Name("Distance function")
    @ReflectObject.Comment("Last entry must be 0")
    DiscreteFunction distance;
    
    public Facility12Rule(int level) {
        super(Arrays.asList(Layers.FACILITY));
        this.level = level;
        if(level == 1) {
            maxDistClust = 1000;
            diversity = new DiscreteFunction(new double[]{0.0, 2.0}, new double []{0.0, 1.0});
            count = new DiscreteFunction(new double[]{0.0, 3.0}, new double []{0.0001, 1.0});
            distance = new DiscreteFunction(new double[]{0.0, 200.0, 600.0}, new double []{1.0, 0.5, 0.0});
        } else { // level 2
            maxDistClust = 3000;
            diversity = new DiscreteFunction(new double[]{0.0, 12.0}, new double []{0.0, 1.0});
            count = new DiscreteFunction(new double[]{0.0, 15.0}, new double []{0.0001, 1.0});
            distance = new DiscreteFunction(new double[]{0.0, 2000.0}, new double []{1.0, 0.0});
        }
    }

    @Override
    public String getName() {
        return "fac" + level;
    }

    @Override
    public void createRule(final Project project) {

        // charge coverage service du niveau level
        DefaultFeatureCoverage<DefaultFeature> facCov = project.getCoverageLevel(Layers.FACILITY, level);
        
        if (level == 2)
        {
            // charge coverage service du niveau level 1
            final DefaultFeatureCoverage<DefaultFeature> facCovLevel1 = project.getCoverageLevel(Layers.FACILITY, 1);// extrait l'ensemble des points
            //récupère la liste des features de level 1
            List<DefaultFeature> featuresLevel1 = new ArrayList<DefaultFeature>(facCovLevel1.getFeatures());

            //récupère la liste des features de level 2
            List<DefaultFeature> featuresLevel2 = facCov.getFeatures();
            
            // Concaténation des deux listes
            featuresLevel1.addAll(featuresLevel2); 
            
            // créer le nouveau coverage avec l'esnemble des features niveau 1 et 2
            facCov = new DefaultFeatureCoverage(featuresLevel1);
        }
               
        // extrait l'ensemble des points
        List<Geometry> geoms = new ArrayList<Geometry>(facCov.getFeatures().size());
        for(Feature f : facCov.getFeatures())
            geoms.add(f.getGeometry());
        
        // création du buffer pour créer les clusters de services
        Geometry bufFac = BufferTask.buffer(new GeometryFactory().buildGeometry(geoms), distClust/2, 8);
        // Créer les clusters à partir du buffer
        List<ClusterFeature> clusters = new ArrayList<ClusterFeature>();
        for(int i = 0; i < bufFac.getNumGeometries(); i++) {
            List<DefaultFeature> facIn = new ArrayList<DefaultFeature>();
            
            // variable qui test l'existence dans une cluster de niveau 2
            boolean featureLevel2 = false;
            for(DefaultFeature fac : facCov.getFeaturesIn(bufFac.getGeometryN(i)))
            {
                // récupère le level du default feature 
                if(((Number)fac.getAttribute(Project.LEVEL_FIELD)).intValue() == 2)
                    featureLevel2 = true;
                facIn.add(fac);
            }
            //ajout du cluster: si le niveau est 1 ou si le niveau est 2 on ajotuer si exitence un services de niveau 2
            if (level ==1 || featureLevel2)
            {
                clusters.add(new ClusterFeature(i, facIn));
            }
        }
        
        // créer un coverage pour les clusters
        final DefaultFeatureCoverage<ClusterFeature> clusterCov = new DefaultFeatureCoverage<ClusterFeature>(clusters);
        
        // calcule pour chaque cellule la règle d'accessibilité
        project.getMSGrid().addLayer(getName(), DataBuffer.TYPE_FLOAT, Float.NaN);
        project.getMSGrid().execute(new AbstractLayerOperation(4) {
            @Override
            public void perform(Cell cell) {
                // récupère la géométrie de la cellule
                Polygon cellGeom = cell.getGeometry();
                Envelope envMax = new Envelope(cellGeom.getEnvelopeInternal());
                double maxDistNearestFac = distance.getPoints().lastKey();
                // augmente l'enveloppe de maxDistNearestFac
                envMax.expandBy(maxDistNearestFac);
                // récupère les clusters intersectant l'enveloppe
                List<ClusterFeature> cellClusters = clusterCov.getFeatures(envMax);
                if(cellClusters.isEmpty()) {
                    cell.setLayerValue(getName(), 0);
                    return;
                }
                // calcule de distance à partir de la cellule
                OriginDistance origDistance = project.getDistance(cellGeom, maxDistClust);
                
                /// recréé les clusters en ne prenant que les services qui ont une distance inférieure à maxDistClust
                List<ClusterFacility> clusters = new ArrayList<ClusterFacility>();
                for(ClusterFeature clust : cellClusters) {
                    List<DefaultFeature> facIn = new ArrayList<DefaultFeature>();
                    double distMin = Double.MAX_VALUE;
                    for(DefaultFeature fac : clust.getFacilities()) {
                        double d = origDistance.getDistance((Point)fac.getGeometry());
                        if(d < maxDistClust)
                            facIn.add(fac);
                        if(d < distMin)
                            distMin = d;
                    }
                    // créé un nouveau cluster avec les services facIn et la distance minimale distMin
                    if(!facIn.isEmpty() && distMin <= maxDistNearestFac)
                        clusters.add(new ClusterFacility(facIn, distMin));
                }

                double phi = 1;
                for(ClusterFacility clust : clusters) {
                    double d = distance.getValue(clust.getDistMin());
                    double n = count.getValue(clust.getNbFacilities());
                    double delta = diversity.getValue(clust.getNbTypeFacilities());
                    double attract = Math.pow(Math.pow(n, 1-delta)*d, 1-d) * Math.pow(1-(1-Math.pow(n, 1-delta))*(1-d), d);
                    phi *= 1-attract;
                }
                cell.setLayerValue(getName(), 1 - phi);
            }
        }, true);
        
    }
    
    public static class ClusterFacility {
        List<DefaultFeature> facilities;
        double distMin;

        public ClusterFacility(List<DefaultFeature> facilities, double distMin) {
            this.facilities = facilities;
            this.distMin = distMin;
        }

        public double getNbFacilities() {
            return facilities.size();
        }

        public double getNbTypeFacilities() {
            HashSet types = new HashSet();
            for(Feature f : facilities)
                types.add(f.getAttribute(Project.TYPE_FIELD));
            return types.size();
        }
        
//        public double getMinDistance(OriginDistance distance) {
//            double minDist = Double.MAX_VALUE;
//            for(Feature f : facilities) {
//                double d = distance.getDistance((Point)f.getGeometry());
//                if(d < minDist)
//                    minDist = d;
//            }
//            return minDist;
//                
//        }

        public double getDistMin() {
            return distMin;
        }
        
        
    }
    
    public static class ClusterFeature extends AbstractFeature{

        private Integer id;
        private List<DefaultFeature> facilities;
        private MultiPoint geom;


        /** Creates a new instance of ClusterService */
        public ClusterFeature(int id, List<DefaultFeature> facilities) {
            this.facilities = facilities;
            this.id = id;
            Point[] points = new Point[facilities.size()];
            for(int i = 0; i < points.length; i++)
                points[i] = (Point)facilities.get(i).getGeometry();
            geom = new GeometryFactory().createMultiPoint(points);
        }

        public int getNbFacilities() {
            return facilities.size();
        }

        public List<DefaultFeature> getFacilities() {
            return facilities;
        }

        public Object getId() {
            return id;
        }

        public MultiPoint getGeometry() {
            return geom;
        }

        public Object getAttribute(int ind) {
            return null;
        }

        public Object getAttribute(String name) {
            return null;
        }

        public Class getAttributeType(int ind) {
            return Void.TYPE;
        }

        public List<String> getAttributeNames() {
            return Collections.EMPTY_LIST;
        }

        public List<Object> getAttributes() {
            return Collections.EMPTY_LIST;
        }

        public Class getIdType() {
            return Integer.class;
        }
    }
    
}
