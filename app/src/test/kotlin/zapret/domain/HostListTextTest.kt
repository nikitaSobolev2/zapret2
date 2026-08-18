package zapret.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class HostListTextTest {

    @Test
    fun pastedUrlBecomesBareHost() {
        assertEquals(
            "cdn.example.com\n",
            HostListText.normalizeHosts("https://User:pass@cdn.example.com:443/video?x=1\n"),
        )
    }

    @Test
    fun wildcardAndCaseFoldToHostlistForm() {
        assertEquals(
            "example.com\n",
            HostListText.normalizeHosts("*.Example.COM\n"),
        )
    }

    @Test
    fun keepsStrictMatchCaret() {
        assertEquals(
            "^example.com\n",
            HostListText.normalizeHosts("^Example.com\n"),
        )
    }

    @Test
    fun lastLineWithoutNewlineIsKept() {
        assertEquals("example.com\n", HostListText.normalizeHosts("example.com"))
    }

    @Test
    fun crlfAndBomAreStripped() {
        assertEquals(
            "one.example\ntwo.example\n",
            HostListText.normalizeHosts("\uFEFFone.example\r\ntwo.example\r"),
        )
    }

    @Test
    fun commentsStayComments() {
        assertEquals(
            "# mine\nexample.com\n",
            HostListText.normalizeHosts("# mine\nexample.com\n"),
        )
    }

    @Test
    fun emptyInputStaysEmptyFile() {
        assertEquals("", HostListText.normalizeHosts("  \n\n"))
    }

    @Test
    fun ipv6IsNotTreatedAsHostPort() {
        assertEquals(
            "2001:db8::1\n",
            HostListText.normalizeHosts("2001:db8::1\n"),
        )
    }

    @Test
    fun idnBecomesPunycode() {
        assertEquals(
            "xn--e1afmkfd.xn--p1ai\n",
            HostListText.normalizeHosts("пример.рф\n"),
        )
    }

    @Test
    fun ipsetTrimsButKeepsCidr() {
        assertEquals(
            "1.2.3.4/32\n",
            HostListText.normalizeIpset("\r\n  1.2.3.4/32  \n"),
        )
    }
}
