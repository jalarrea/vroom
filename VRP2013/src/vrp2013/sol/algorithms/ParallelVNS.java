package vrp2013.sol.algorithms;

import java.util.Map;
import java.util.Map.Entry;

import vroom.common.heuristics.ConstraintHandler;
import vroom.common.modeling.dataModel.INodeVisit;
import vroom.common.modeling.dataModel.IVRPInstance;
import vroom.common.modeling.dataModel.RouteBase;
import vroom.common.modeling.util.IRoutePool;
import vroom.common.utilities.optimization.OptimizationSense;
import vrp2013.algorithms.VNS;
import vrp2013.util.BatchExecutor;
import vrp2013.util.BatchExecutor.Result;
import vrp2013.util.VRPLogging;
import vrp2013.util.VRPSolution;

/**
 * The class <code>ParallelVND</code> is an implementation of VND that explores neighborhoods in parallel.
 * <p>
 * Creation date: 13/05/2013 - 9:36:40 AM
 * 
 * @author Victor Pillac, <a href="http://www.nicta.com.au">National ICT Australia</a>, <a
 *         href="http://www.victorpillac.com">www.victorpillac.com</a>
 * @version 1.0
 */
public class ParallelVNS extends VNS {

    private final BatchExecutor mExecutor;

    public ParallelVNS(IVRPInstance instance, ConstraintHandler<VRPSolution> constraintHandler,
            IRoutePool<INodeVisit> routePool) {
        super(instance, constraintHandler, routePool);

        // Initialize the thread pool executor
        mExecutor = new BatchExecutor(Math.min(Runtime.getRuntime().availableProcessors(),
                getExplorers().size()), "pvns");
    }

    @Override
    public boolean localSearch(final VRPSolution solution) {
        VRPLogging.logOptResults("ls-init", false, getNeighStopwatch(), solution);

        // Start with the current solution
        VRPSolution bestNeighbor = solution;
        // A flag set to true if the solution was changed in the last iteration
        boolean changedLastIt = true;
        // A flag set to true if the solution was changed at least once
        boolean changedOverall = false;
        while (changedLastIt) {
            // The current solution is the best neighbor from the previous iteration
            VRPSolution currentSolution = bestNeighbor;
            // The explorer that found the best solution (for logging purposes)
            NeighborhoodExplorer bestExplorer = null;
            changedLastIt = false;
            getNeighStopwatch().restart();

            for (NeighborhoodExplorer explorer : getExplorers()) {
                // Set the starting solution for this explorer
                explorer.setStartSolution(currentSolution);
            }

            Map<NeighborhoodExplorer, Result<VRPSolution>> results = null;
            // Explore the neighborhoods in parallel threads
            results = getExecutor().submitBatchAndWait(getExplorers());
            // Find the best neighbor in the results
            for (Entry<NeighborhoodExplorer, Result<VRPSolution>> entry : results.entrySet()) {
                if (OptimizationSense.MINIMIZATION.isBetter(bestNeighbor, entry.getValue().get())) {
                    changedLastIt = true;
                    changedOverall = true;
                    bestNeighbor = entry.getValue().get();
                    bestExplorer = entry.getKey();
                }
            }

            getNeighStopwatch().stop();
            if (changedLastIt) {
                VRPLogging.logOptResults(bestExplorer.getShakeNeighborhood().getShortName() + "*",
                        changedLastIt, getNeighStopwatch(), bestNeighbor);
                setBestSolution(bestNeighbor);
            }
        }

        // Copy the best solution to the solution passed as argument
        if (changedOverall) {
            solution.clear();
            for (RouteBase r : bestNeighbor)
                if (r.length() > 2)
                    solution.addRoute(r);
        }

        return changedOverall;
    }

    @Override
    public void dispose() {
        getExecutor().shutdown();
    }

    public BatchExecutor getExecutor() {
        return mExecutor;
    }
}
