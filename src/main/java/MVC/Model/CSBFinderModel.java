package MVC.Model;

import MVC.Common.*;
import Main.*;
import PostProcess.Family;
import PostProcess.FamilyClustering;
import SuffixTrees.*;
import Utils.Utils;
import Utils.Gene;
import Utils.COG;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

import java.util.*;

public class CSBFinderModel {

    private GenomesLoadedListener genomesLoadedListener;
    private CSBFinderDoneListener csbFinderDoneListener;

    private CommandLineArgs cla;
    private Utils utils;
    private Writer writer;
    private GeneralizedSuffixTree dataset_suffix_tree;
    private ArrayList<Family> families;

    private int number_of_genomes = -1;

    public CSBFinderModel() {
        this.init();
    }

    public void init() {
        this.utils = new Utils(null, null);
    }

    public void loadFile(String path, boolean is_directons) {
        dataset_suffix_tree = new GeneralizedSuffixTree();
        number_of_genomes = utils.readAndBuildDatasetTree(path,
                dataset_suffix_tree, cla.is_directons);
        genomesLoadedListener.genomesLoadDone(new GenomesLoadEvent(utils.getGenomeToGeneListMap()));

    }

    public void findCSBs(CSBFinderRequest request) {
        String[] args = request.toArgArray();
        JCommander jcommander;
        try {
            cla = new CommandLineArgs();

            jcommander = JCommander.newBuilder().addObject(cla).build();
            jcommander.parse(args);
            writer = createWriter(cla.cog_info_file_name != null && !"".equals(cla.cog_info_file_name));

            this.findCSBs();
        } catch (ParameterException e){
            System.err.println(e.getMessage());

            jcommander = JCommander.newBuilder().addObject(cla).build();
//            printUsageAndExit(jcommander, 1);
        }
    }

    private void findCSBs() {
        long startTime = System.nanoTime();

        HashMap<String, COG> cog_info = null;
        boolean cog_info_exists = (cla.cog_info_file_name != null);
        if (cog_info_exists) {
            cog_info = Readers.read_cog_info_table(cla.cog_info_file_name);
        }

        utils.setCog_info(cog_info);

        Trie pattern_tree = buildPatternsTree(utils);

        System.out.println("Extracting CSBs from " + number_of_genomes + " input sequences.");

        CSBFinder csbFinder = new CSBFinder(cla.max_error, cla.max_wildcards, cla.max_deletion, cla.max_insertion,
                cla.quorum1, cla.quorum2,
                cla.min_pattern_length, cla.max_pattern_length, utils.GAP_CHAR_INDEX, utils.WC_CHAR_INDEX,
                dataset_suffix_tree, pattern_tree, cla.bool_count, utils, cla.memory_saving_mode, writer,
                cla.is_directons, cla.debug);

        if (cla.input_patterns_file_name == null) {
            csbFinder.removeRedundantPatterns();
        }

        ArrayList<Pattern> patterns = csbFinder.getPatterns();

        for (Pattern pattern : patterns) {
            pattern.calculateScore(utils, cla.max_insertion, cla.max_error, cla.max_deletion);
            pattern.calculateMainFunctionalCategory(utils, cla.is_directons);
        }

        System.out.println("Clustering to families");
        families = FamilyClustering.Cluster(patterns, cla.threshold, cla.cluster_by, utils,
                cla.is_directons);

        long patternCount = 0;
        for (Family family : families) {
            patternCount += family.getPatterns().stream().filter(pattern -> pattern != null).count();
        }

        System.out.println(patternCount + " CSBs found");
        System.out.println("Took " + String.valueOf((System.nanoTime() - startTime) / Math.pow(10, 9)) + " seconds");

        csbFinderDoneListener.CSBFinderDoneOccurred(new CSBFinderDoneEvent(families));
    }

    private Writer createWriter(boolean cog_info_exists){
        String parameters = "_ins" + cla.max_insertion + "_q" + cla.quorum2;
        String catalog_file_name = "Catalog_" + cla.dataset_name + parameters;
        String instances_file_name = catalog_file_name + "_instances";
        boolean include_families = true;
        if (cla.memory_saving_mode) {
            include_families = false;
        }

        Writer writer = new Writer(cla.max_error, cla.max_deletion, cla.max_insertion, cla.debug, catalog_file_name,
                instances_file_name,
                include_families, cla.output_file_type, cog_info_exists, cla.is_directons);

        return writer;
    }

    public void saveOutputFiles(String outputFileType) {
        writer.setOutput_file_type(CommandLineArgs.OutputType.valueOf(outputFileType));
        System.out.println("Writing to files");
        for (Family family : families) {
            writer.printFilteredCSB(family.getPatterns().get(0), utils, family.getFamilyId());
            for (Pattern pattern : family.getPatterns()) {
                writer.printPattern(pattern, utils, family.getFamilyId());
            }
        }
        writer.closeFiles();
    }

    private Trie buildPatternsTree(Utils utils) {
        Trie pattern_tree = null;
        if (cla.input_patterns_file_name != null) {
            //these arguments are not valid when input patterns are give
            cla.min_pattern_length = 2;
            cla.max_pattern_length = Integer.MAX_VALUE;

            pattern_tree = new Trie(TreeType.STATIC);
            String path = cla.input_patterns_file_name;
            if (!utils.buildPatternsTreeFromFile(path, pattern_tree)){
                pattern_tree = null;//if tree building wasn't successful
            }
        }
        return pattern_tree;
    }

    public Map<String, List<Gene>> getGenomeToGeneListMap() {
        return utils.getGenomeToGeneListMap();
    }

    public List<Family> getFamilies() {
        return families;
    }

    public void setGenomesLoadedListener(GenomesLoadedListener genomesLoadedListener) {
        this.genomesLoadedListener = genomesLoadedListener;
    }

    public void setCSBFinderDoneListener(CSBFinderDoneListener csbFinderDoneListener) {
        this.csbFinderDoneListener = csbFinderDoneListener;
    }

    public Map<String, String> getCogInfo(List<String> cogs) {
        Map<String, String> cogInfo = new HashMap<>();
        if (utils.getCog_info() != null) {
            cogs.forEach(cog -> {
                COG c = utils.getCog_info().get(cog);
                if (c != null) {
                    cogInfo.put(cog, c.getCog_desc());
                }
            });
        }

        return cogInfo;
    }

    public Map<String, List<List<Gene>>> getInstances(Pattern pattern) {
        HashMap<String, List<List<Gene>>> instance_seq_and_location = new HashMap<>();
        for (Instance instance : pattern.get_instances()) {

            InstanceNode instance_node = instance.getNodeInstance();
            if (instance.getEdge() != null) {
                Edge edge = instance.getEdge();
                instance_node = (InstanceNode) edge.getDest();
            }

            for (Map.Entry<Integer, ArrayList<Integer[]>> entry : instance_node.getResults().entrySet()) {

                String seq_name = utils.genome_key_to_name.get(entry.getKey());
                if (!instance_seq_and_location.containsKey(seq_name)) {
                    instance_seq_and_location.put(seq_name, new ArrayList<>());
                }

                List<List<Gene>> instances_info = instance_seq_and_location.get(seq_name);
                for (Integer[] instance_info : entry.getValue()) {
                    int startIndex = instance_info[1];
                    int instanceLen = instance_info[2];

                    // Check that indexes make sense and In range of the Genome cog list
                    if (startIndex >= 0 && instanceLen > 0) {
                        List<Gene> instanceList = new ArrayList<>();
                        instanceList.addAll(getInstanceFromCogList(seq_name, startIndex, instanceLen));
                        if (instanceList != null) {
                            instances_info.add(instanceList);
                        }
                    } else {
                        writer.writeLogger(String.format("WARNING: Instance in sequence %s indexes are out of range. " +
                                "repliconKey: %s,start: %s, length: %s",
                                seq_name, instance_info[0], instance_info[1], instance_info[2]));
                    }
                }
            }
        }

        return instance_seq_and_location;
    }

    private List<Gene> getInstanceFromCogList(String seq_name, int startIndex, int instanceLength) {
        List<Gene> instanceList = null;
        List<Gene> genomeToCogList = utils.getGenomeToGeneListMap().get(seq_name);
        if (genomeToCogList != null) {
            if (startIndex < genomeToCogList.size() && genomeToCogList.size() > startIndex + instanceLength) {
                instanceList = genomeToCogList.subList(startIndex, startIndex + instanceLength);
            } else {
                writer.writeLogger(String.format("WARNING: replicon is out of bound in sequence %s, start: %s,length: %s",
                        seq_name, startIndex, instanceLength));
            }
        } else {
            writer.writeLogger(String.format("WARNING: Genome %s not found", seq_name));
        }

        return instanceList;
    }
}
