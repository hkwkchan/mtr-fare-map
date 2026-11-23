package com.example.fare;

import com.example.fare.DataService.LineResponse;
import com.example.fare.DataService.RouteResponse;
import com.example.fare.DataService.Station;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "MTR", description = "MTR route and station-line lookup endpoints")
public class ApiController {
	private final DataService mtrDataService;

	public ApiController(DataService mtrDataService) {
		this.mtrDataService = mtrDataService;
	}

    @GetMapping("/stations")
    @Operation(
            summary = "Search stations by name or code",
            description = "Returns stations whose English name, Chinese name, or station code contains the query.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Matching stations"),
        @ApiResponse(responseCode = "400", description = "The search query is missing or blank")
    })
    public List<Station> stations(
            @RequestParam @Parameter(description = "Partial station name or code", example = "Tuen Mu") String query) {
        return mtrDataService.searchStations(query);
    }

    @GetMapping("/stations/all")
    @Operation(summary = "List all stations", description = "Returns all stations for client-side searching.")
    @ApiResponse(responseCode = "200", description = "All stations")
    public List<Station> allStations() {
        return mtrDataService.allStations();
    }

	@GetMapping("/route")
	@Operation(
			summary = "Find a route with the fewest line changes",
			description = "Finds a route by unique station IDs, minimizing line changes first and adjacent "
					+ "station stops second. The route includes line legs and fare categories in HK cents "
                    + "when fare data is available.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Route found"),
		@ApiResponse(responseCode = "400", description = "A station ID is missing or blank"),
		@ApiResponse(responseCode = "404", description = "A station ID is unknown or no route exists")
	})
	public RouteResponse route(
			@RequestParam @Parameter(description = "Starting station ID", example = "1") String start,
			@RequestParam @Parameter(description = "Destination station ID", example = "3") String dest) {
		return mtrDataService.findRoute(start, dest);
	}

	@GetMapping("/lines/{line}")
	@Operation(
			summary = "List stations on a line",
			description = "Returns all unique stations for a line and the ordered station sequence for each direction.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Line found"),
		@ApiResponse(responseCode = "404", description = "The line code is unknown")
	})
	public LineResponse line(
			@PathVariable @Parameter(description = "MTR line code (case-insensitive)", example = "AEL") String line) {
		return mtrDataService.findLine(line);
	}
}
