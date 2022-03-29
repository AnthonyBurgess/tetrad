package edu.cmu.tetrad.algcomparison.algorithm.external;

import edu.cmu.tetrad.algcomparison.algorithm.ExternalAlgorithm;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * An API to allow results from external algorithms to be included in a report through the algrorithm
 * comparison tool. This one is for matrix generated by PC in pcalg. See below. This script can generate
 * the files in R.
 * <p>
 * library("MASS");
 * library("pcalg");
 * <p>
 * path<-"/Users/user/tetrad/comparison-final";
 * simulation<-1;
 * <p>
 * subdir<-"pc.solve.confl.TRUE";
 * dir.create(paste(path, "/save/", simulation, "/", subdir, sep=""));
 * <p>
 * for (i in 1:10) {
 * data<-read.table(paste(path, "/save/", simulation, "/data/data.", i, ".txt", sep=""), header=TRUE)
 * n<-nrow(data)
 * C<-cor(data)
 * v<-names(data)
 * suffStat<-list(C = C, n=n)
 * pc.fit<-pc(suffStat=suffStat, indepTest=gaussCItest, alpha=0.001, labels=v,
 * solve.conf=TRUE)
 * A<-as(pc.fit, "amat")
 * name<-paste(path, "/save/", simulation, "/", subdir, "/graph.", i, ".txt", sep="")
 * print(name)
 * write.matrix(A, file=name, sep="\t")
 * }
 *
 * @author jdramsey
 */
public class ExternalAlgorithmPcalgGes extends ExternalAlgorithm {
    static final long serialVersionUID = 23L;
    private final String extDir;
    private String shortDescription;

    public ExternalAlgorithmPcalgGes(String extDir) {
        this.extDir = extDir;
        shortDescription = new File(extDir).getName().replace("_", " ");
    }

    public ExternalAlgorithmPcalgGes(String extDir, String shortDecription) {
        this.extDir = extDir;
        shortDescription = shortDecription;
    }

    /**
     * Reads in the relevant graph from the file (see above) and returns it.
     */
    public Graph search(DataModel dataSet, Parameters parameters) {
        int index = this.getIndex(dataSet);

        File nodes = new File(path, "/results/" + extDir + "/" + (simIndex + 1) + "/nodes." + index + ".txt");
        System.out.println(nodes.getAbsolutePath());

        List<Node> vars = new ArrayList<>();

        try {
            BufferedReader r = new BufferedReader(new FileReader(nodes));
            String name;

            while ((name = r.readLine()) != null) {
                GraphNode node = new GraphNode(name.trim());
                vars.add(node);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        File inEdges = new File(path, "/results/" + extDir + "/" + (simIndex + 1) + "/in.edges." + index + ".txt");

        try {
            BufferedReader r = new BufferedReader(new FileReader(inEdges));
            String line;
            Graph graph = new EdgeListGraph(vars);

            for (int i = 0; i < vars.size(); i++) {
                line = r.readLine();
                String[] tokens = line.split(",");

                for (String token : tokens) {
                    String trim = token.trim();
                    if (trim.isEmpty()) continue;
                    int j = Integer.parseInt(trim) - 1;
                    Node v1 = vars.get(i);
                    Node v2 = vars.get(j);

                    if (!graph.isAdjacentTo(v2, v1)) {
                        graph.addDirectedEdge(v2, v1);
                    } else {
                        graph.removeEdge(v2, v1);
                        graph.addUndirectedEdge(v2, v1);
                    }
                }
            }

            return graph;
        } catch (Exception e) {
            e.printStackTrace();
        }

        throw new IllegalArgumentException("Could not parse graph.");
    }

    /**
     * Returns the CPDAG of the supplied DAG.
     */
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    public String getDescription() {
        if (shortDescription == null) {
            return "Load data from " + path + "/" + extDir;
        } else {
            return shortDescription;
        }
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public long getElapsedTime(DataModel dataSet, Parameters parameters) {
        int index = this.getIndex(dataSet);

        File file = new File(path, "/elapsed/" + extDir + "/" + (simIndex + 1) + "/graph." + index + ".txt");

        try {
            BufferedReader r = new BufferedReader(new FileReader(file));
            String l = r.readLine(); // Skip the first line.
            return Long.parseLong(l);
        } catch (IOException e) {
            return -99;
        }
    }

}


