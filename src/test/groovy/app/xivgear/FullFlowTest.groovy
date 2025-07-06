package app.xivgear

import app.xivgear.userstorage.dto.*
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.netty.DefaultHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.authentication.Authentication
import io.micronaut.security.token.claims.ClaimsGenerator
import io.micronaut.security.token.jwt.generator.JwtTokenGenerator
import io.micronaut.security.token.jwt.signature.SignatureGeneratorConfiguration
import io.micronaut.security.token.jwt.signature.secret.SecretSignature
import io.micronaut.security.token.jwt.signature.secret.SecretSignatureConfiguration
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.annotation.PostConstruct
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.junit.jupiter.api.Test

import java.security.SecureRandom

import static org.junit.jupiter.api.Assertions.*

@MicronautTest
@CompileStatic
@Slf4j
class FullFlowTest {

	@Singleton
	@Inject
	EmbeddedServer server

	@Inject
	@Client
	DefaultHttpClient client

	@Inject
	JwtTokenGenerator tokenGenerator

	private final int UID = new SecureRandom().nextInt 1_000_000, Integer.MAX_VALUE

	private String validToken

	@PostConstruct
	void configureClient() {
		log.info "Generated UID: ${UID}"
		client.configuration.exceptionOnErrorStatus = false
		Authentication auth = Authentication.build(
				UID.toString(),
				['verified'],
				[userId: UID] as Map<String, Object>
		)
		validToken = tokenGenerator.generateToken(auth, 30 * 60).orElseThrow()
	}

	void addHeaders(MutableHttpRequest<?> req, String token = validToken) {
		req.header 'Authorization', "Bearer ${token}"
		req.header 'xivgear-csrf', '1'
	}

	@Test
	void testFullFlow() {
		// Should start with no preferences
		// TODO: verify response headers for caching
		{
			HttpRequest<?> req = HttpRequest.GET(server.URI.resolve("userdata/preferences")).tap {
				addHeaders it
			}
			HttpResponse<GetPreferencesResponse> response = client.toBlocking().exchange req, GetPreferencesResponse
			assertEquals HttpStatus.OK, response.status
			assertFalse response.body().found
			assertNull response.body().preferences
		}

		// Initial preferences upload
		{
			var prefs = new UserPreferences().tap {
				lightMode = true
				languageOverride = 'foo'
			}
			var prefsReq = new PutPreferencesRequest().tap {
				preferences = prefs
			}

			HttpRequest<PutPreferencesRequest> req = HttpRequest.PUT(server.URI.resolve("userdata/preferences"), prefsReq).tap {
				addHeaders it
			}
			HttpResponse<?> response = client.toBlocking().exchange req
			assertEquals HttpStatus.OK, response.status
		}

		// Retrieve uploaded prefs
		{
			HttpRequest<?> req = HttpRequest.GET(server.URI.resolve("userdata/preferences")).tap {
				addHeaders it
			}

			HttpResponse<GetPreferencesResponse> response = client.toBlocking().exchange req, GetPreferencesResponse
			assertEquals HttpStatus.OK, response.status
			assertTrue response.body().found
			assertEquals true, response.body().preferences.lightMode
			assertEquals 'foo', response.body().preferences.languageOverride
		}

		// Update prefs
		{
			var prefs = new UserPreferences().tap {
				lightMode = false
				languageOverride = 'bar'
			}
			var prefsReq = new PutPreferencesRequest().tap {
				preferences = prefs
			}

			HttpRequest<PutPreferencesRequest> req = HttpRequest.PUT(server.URI.resolve("userdata/preferences"), prefsReq).tap {
				addHeaders it
			}
			HttpResponse<?> response = client.toBlocking().exchange req
			assertEquals HttpStatus.OK, response.status
		}

		// Retrieve again
		{
			HttpRequest<?> req = HttpRequest.GET(server.URI.resolve("userdata/preferences")).tap {
				addHeaders it
			}

			HttpResponse<GetPreferencesResponse> response = client.toBlocking().exchange req, GetPreferencesResponse
			assertEquals HttpStatus.OK, response.status
			assertTrue response.body().found
			assertEquals false, response.body().preferences.lightMode
			assertEquals 'bar', response.body().preferences.languageOverride
		}

		// Get sheets - should be empty
		{
			HttpRequest<?> req = HttpRequest.GET(server.URI.resolve("userdata/sheets")).tap {
				addHeaders it
			}

			HttpResponse<GetSheetsResponse> response = client.toBlocking().exchange req, GetSheetsResponse
			assertEquals HttpStatus.OK, response.status
			assertTrue response.body().sheets.isEmpty()
		}

		var summary = new SheetSummary().tap {
			name = 'Test Sheet'
			level = 100
			job = 'WHM'
		}

		String sheetKey = 'sheet-save-123-foobar';
		// Upload a new sheet
		{
			var sheetReq = new PutSheetRequest().tap {
				sheetData = [foo: 'bar']
				lastSyncedVersion = 5
				newSheetVersion = 6
				newSheetVersionKey = null
				sheetSummary = summary
				sortOrder = 3.45
			}

			HttpRequest<PutSheetRequest> req = HttpRequest.PUT(server.URI.resolve("userdata/sheets/${sheetKey}"), sheetReq).tap {
				addHeaders it
			}
			HttpResponse<String> response = client.toBlocking().exchange req, Argument.of(String), Argument.of(String)
			assertEquals HttpStatus.OK, response.status
		}

		// Verify sheet appears in list
		{
			HttpRequest<?> req = HttpRequest.GET(server.URI.resolve("userdata/sheets")).tap {
				addHeaders it
			}

			HttpResponse<GetSheetsResponse> response = client.toBlocking().exchange req, GetSheetsResponse
			assertEquals HttpStatus.OK, response.status
			assertEquals 1, response.body().sheets.size()
			assertEquals 'Test Sheet', response.body().sheets[0].summary.name
			assertEquals 'WHM', response.body().sheets[0].summary.job
			assertEquals false, response.body().sheets[0].summary.multiJob
			assertEquals 3.45 as Double, response.body().sheets[0].sortOrder
			assertEquals 0, response.body().sheets[0].versionKey
		}

		// Get specific sheet
		{
			HttpRequest<?> req = HttpRequest.GET(server.URI.resolve("userdata/sheets/${sheetKey}")).tap {
				addHeaders it
			}

			HttpResponse<GetSheetResponse> response = client.toBlocking().exchange req, GetSheetResponse
			assertEquals HttpStatus.OK, response.status
			assertNotNull response.body()
			assertEquals([foo: 'bar'], response.body().sheetData)
			assertEquals 6, response.body().metadata.version
			assertEquals 3.45 as Double, response.body().metadata.sortOrder
		}

		// Try updating with outdated version
		// Server has version 6 at this point, so if the client thinks the server only has version 3, then
		{
			var sheetReq = new PutSheetRequest().tap {
				sheetData = [foo: 'baz']
				lastSyncedVersion = 3
				newSheetVersion = 7    // Trying to set new version while server has 6
				newSheetVersionKey = 123
				sheetSummary = summary.tap {
					name = 'Sheet Summary Updated'
				}
			}

			HttpRequest<PutSheetRequest> req = HttpRequest.PUT(server.URI.resolve("userdata/sheets/${sheetKey}"), sheetReq).tap {
				addHeaders it
			}
			HttpResponse<?> response = client.toBlocking().exchange req, Argument.of(String), Argument.of(String)
			assertEquals HttpStatus.CONFLICT, response.status
		}
		// Try updating with good version
		// Server has version 6 at this point, so if the client thinks the server only has version 3, then
		{
			var sheetReq = new PutSheetRequest().tap {
				sheetData = [foo: 'baz']
				lastSyncedVersion = 6
				newSheetVersion = 7    // Trying to set new version while server has 6
				newSheetVersionKey = 456
				sheetSummary = summary.tap {
					name = 'Sheet Summary Updated'
				}
			}

			HttpRequest<PutSheetRequest> req = HttpRequest.PUT(server.URI.resolve("userdata/sheets/${sheetKey}"), sheetReq).tap {
				addHeaders it
			}
			HttpResponse<?> response = client.toBlocking().exchange req, Argument.of(String), Argument.of(String)
			assertEquals HttpStatus.OK, response.status
		}
		// Get updated version
		{
			HttpRequest<?> req = HttpRequest.GET(server.URI.resolve("userdata/sheets/${sheetKey}")).tap {
				addHeaders it
			}

			HttpResponse<GetSheetResponse> response = client.toBlocking().exchange req, GetSheetResponse
			assertEquals HttpStatus.OK, response.status
			assertNotNull response.body()
			assertEquals([foo: 'baz'], response.body().sheetData)
			assertEquals 7, response.body().metadata.version
			assertEquals 456, response.body().metadata.versionKey
			assertEquals 'Sheet Summary Updated', response.body().metadata.summary.name
			assertNull response.body().metadata.sortOrder
		}

		// Try deleting with outdated version
		{
			HttpRequest<?> req = HttpRequest.DELETE(server.URI.resolve("userdata/sheets/${sheetKey}"), new DeleteSheetRequest().tap {
				it.lastSyncedVersion = 3  // Server has version 7 at this point
				it.newSheetVersion = 5
			}).tap {
				addHeaders it
			}
			HttpResponse<DeleteSheetResponse> response = client.toBlocking().exchange req, Argument.of(DeleteSheetResponse), Argument.of(DeleteSheetResponse)
			assertEquals HttpStatus.CONFLICT, response.status
			assertFalse response.body().success
			assertTrue response.body().conflict
		}

		// Delete sheet
		{
			HttpRequest<?> req = HttpRequest.DELETE(server.URI.resolve("userdata/sheets/${sheetKey}"), new DeleteSheetRequest().tap {
				it.lastSyncedVersion = 7
				it.newSheetVersion = 8
				it.newSheetVersionKey = 789
			}).tap {
				addHeaders it
			}
			HttpResponse<?> response = client.toBlocking().exchange req
			assertEquals HttpStatus.OK, response.status
		}

		// Verify direct get indicates sheet is deleted
		{
			HttpRequest<?> req = HttpRequest.GET(server.URI.resolve("userdata/sheets/${sheetKey}")).tap {
				addHeaders it
			}

			HttpResponse<GetSheetResponse> response = client.toBlocking().exchange req, Argument.of(GetSheetResponse), Argument.of(GetSheetResponse)
			assertEquals HttpStatus.OK, response.status
			assertTrue response.body().metadata.deleted
			assertEquals 8, response.body().metadata.version
			assertEquals 789, response.body().metadata.versionKey
		}

		// Verify deleted sheet appears in metadata
		{
			HttpRequest<?> req = HttpRequest.GET(server.URI.resolve("userdata/sheets")).tap {
				addHeaders it
			}

			HttpResponse<GetSheetsResponse> response = client.toBlocking().exchange req, GetSheetsResponse
			assertEquals HttpStatus.OK, response.status
			assertEquals 1, response.body().sheets.size()
			assertTrue response.body().sheets[0].deleted
			assertEquals sheetKey, response.body().sheets[0].saveKey
			assertEquals 8, response.body().sheets[0].version
			assertEquals 789, response.body().sheets[0].versionKey
		}

	}

	@Test
	void testUnauth() {


		HttpRequest<?> req = HttpRequest.GET(server.URI.resolve("userdata/preferences")).tap {
			header 'xivgear-csrf', '1'
		}
		HttpResponse<?> response = client.toBlocking().exchange req, Argument.of(String), Argument.of(String)
		assertEquals HttpStatus.UNAUTHORIZED, response.status

		req = HttpRequest.GET(server.URI.resolve("userdata/sheets")).tap {
			header 'xivgear-csrf', '1'
		}
		response = client.toBlocking().exchange req, Argument.of(String), Argument.of(String)
		assertEquals HttpStatus.UNAUTHORIZED, response.status

		var prefs = new UserPreferences().tap {
			lightMode = true
		}
		var prefsReq = new PutPreferencesRequest().tap {
			preferences = prefs
		}
		HttpRequest<PutPreferencesRequest> putReq = HttpRequest.PUT(server.URI.resolve("userdata/preferences"), prefsReq).tap {
			header 'xivgear-csrf', '1'
		}
		response = client.toBlocking().exchange putReq, Argument.of(String), Argument.of(String)
		assertEquals HttpStatus.UNAUTHORIZED, response.status
	}

	@Test
	void testValidTokenButNoCsrf() {
		HttpRequest<?> req = HttpRequest.GET(server.URI.resolve("userdata/preferences")).tap {
			header 'Authorization', "Bearer ${validToken}"
		}
		HttpResponse<?> response = client.toBlocking().exchange req, Argument.of(String), Argument.of(String)
		assertEquals HttpStatus.FORBIDDEN, response.status

		req = HttpRequest.GET(server.URI.resolve("userdata/sheets")).tap {
			header 'Authorization', "Bearer ${validToken}"
		}
		response = client.toBlocking().exchange req, Argument.of(String), Argument.of(String)
		assertEquals HttpStatus.FORBIDDEN, response.status

		var prefs = new UserPreferences().tap {
			lightMode = true
		}
		var prefsReq = new PutPreferencesRequest().tap {
			preferences = prefs
		}
		HttpRequest<PutPreferencesRequest> putReq = HttpRequest.PUT(server.URI.resolve("userdata/preferences"), prefsReq).tap {
			header 'Authorization', "Bearer ${validToken}"
		}
		response = client.toBlocking().exchange putReq, Argument.of(String), Argument.of(String)
		assertEquals HttpStatus.FORBIDDEN, response.status
	}

	@Inject
	ClaimsGenerator cgen

	private String generateInvalidToken() {
		var goodGenerator = tokenGenerator
		SignatureGeneratorConfiguration badSigCfg = new SecretSignature(new SecretSignatureConfiguration("foo").tap {
			it.secret = "bad secret with some extra padding to meet min length"
		})
		var generator = new JwtTokenGenerator(
				badSigCfg,
				goodGenerator.encryptionConfiguration,
				cgen
		)
		Authentication auth = Authentication.build(
				UID.toString(),
				['verified'],
				[userId: UID] as Map<String, Object>
		)
		return generator.generateToken(auth, 30 * 60).orElseThrow()
	}

	private String generateSecondaryValidToken() {
		var goodGenerator = tokenGenerator
		SignatureGeneratorConfiguration badSigCfg = new SecretSignature(new SecretSignatureConfiguration("foo").tap {
			it.secret = 'fakeTokenForTestsDoNotTrustFooooooooooooooooooooooo'
		})
		var generator = new JwtTokenGenerator(
				badSigCfg,
				goodGenerator.encryptionConfiguration,
				cgen
		)
		Authentication auth = Authentication.build(
				UID.toString(),
				['verified'],
				[userId: UID] as Map<String, Object>
		)
		return generator.generateToken(auth, 30 * 60).orElseThrow()
	}

	@Test
	void testInvalidTokenSignature() {
		String invalidToken = generateInvalidToken()

		HttpRequest<?> req = HttpRequest.GET(server.URI.resolve("userdata/preferences")).tap {
			header 'Authorization', "Bearer ${invalidToken}"
			header 'xivgear-csrf', '1'
		}
		HttpResponse<?> response = client.toBlocking().exchange req, Argument.of(String), Argument.of(String)
		assertEquals HttpStatus.UNAUTHORIZED, response.status
	}

	// Test using the valid secret so that we make sure our test is actually testing the right thing
	@Test
	void testValidTokenSignature() {
		String invalidToken = generateSecondaryValidToken()

		HttpRequest<?> req = HttpRequest.GET(server.URI.resolve("userdata/preferences")).tap {
			header 'Authorization', "Bearer ${invalidToken}"
			header 'xivgear-csrf', '1'
		}
		HttpResponse<?> response = client.toBlocking().exchange req, Argument.of(String), Argument.of(String)
		assertEquals HttpStatus.OK, response.status
	}

	@Test
	void testUnverifiedUser() {
		Authentication auth = Authentication.build(
				UID.toString(),
				[], // No roles
				[userId: UID] as Map<String, Object>
		)
		String unverifiedToken = tokenGenerator.generateToken(auth, 30 * 60).orElseThrow()

		HttpRequest<?> req = HttpRequest.GET(server.URI.resolve("userdata/preferences")).tap {
			header 'Authorization', "Bearer ${unverifiedToken}"
			header 'xivgear-csrf', '1'
		}
		HttpResponse<?> response = client.toBlocking().exchange req, Argument.of(String), Argument.of(String)
		assertEquals HttpStatus.FORBIDDEN, response.status

		req = HttpRequest.GET(server.URI.resolve("userdata/sheets")).tap {
			header 'Authorization', "Bearer ${unverifiedToken}"
			header 'xivgear-csrf', '1'
		}
		response = client.toBlocking().exchange req, Argument.of(String), Argument.of(String)
		assertEquals HttpStatus.FORBIDDEN, response.status

		var prefs = new UserPreferences().tap {
			lightMode = true
		}
		var prefsReq = new PutPreferencesRequest().tap {
			preferences = prefs
		}
		HttpRequest<PutPreferencesRequest> putReq = HttpRequest.PUT(server.URI.resolve("userdata/preferences"), prefsReq).tap {
			header 'Authorization', "Bearer ${unverifiedToken}"
			header 'xivgear-csrf', '1'
		}
		response = client.toBlocking().exchange putReq, Argument.of(String), Argument.of(String)
		assertEquals HttpStatus.FORBIDDEN, response.status
	}
}
