/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */
package io.bitsquare.io;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.regex.Pattern;

/**
 * Based on https://github.com/ikkisoft/SerialKiller but we removed the support for a config file as
 * that introduced many dependencies (like the vulnerable commons collections).
 */
public class LookAheadObjectInputStream extends ObjectInputStream {
    private static final Logger log = LoggerFactory.getLogger(LookAheadObjectInputStream.class);

    // Covers classes used in the objects sent over the P2P network.
    // We don't add all particular classes as the risk to miss one (specially at updates) is too high and a package 
    // based filter gives us sufficient security.
    // This white list is not sufficient for the objects used for local persistence! We don't use any white list for those.
    private static final Pattern[] whiteListP2PNetwork = {
            Pattern.compile("io\\.bitsquare\\..*$"),
            Pattern.compile("org\\.bitcoinj\\..*$"),

            Pattern.compile("java\\.lang\\.Boolean$"),
            Pattern.compile("java\\.lang\\.Enum$"),
            Pattern.compile("java\\.lang\\.Integer$"),
            Pattern.compile("java\\.lang\\.Long$"),
            Pattern.compile("java\\.lang\\.Double$"),
            Pattern.compile("java\\.lang\\.Number$"),

            Pattern.compile("java\\.util\\.ArrayList$"),
            Pattern.compile("java\\.util\\.Date$"),
            Pattern.compile("java\\.util\\.HashSet$"),
            Pattern.compile("java\\.util\\.HashMap$"),

            // Type Signatures
            // https://docs.oracle.com/javase/7/docs/technotes/guides/jni/spec/types.html
            Pattern.compile("\\[B$") // byte array
    };

    private final static Pattern[] blackList = {
            Pattern.compile("bsh\\.XThis$"),
            Pattern.compile("bsh\\.Interpreter$"),
            Pattern.compile("com\\.mchange\\.v2\\.c3p0\\.impl\\.PoolBackedDataSourceBase$"),
            Pattern.compile("org\\.apache\\.commons\\.beanutils\\.BeanComparator$"),
            Pattern.compile("org\\.apache\\.commons\\.collections\\.Transformer$"),
            Pattern.compile("org\\.apache\\.commons\\.collections\\.functors\\.InvokerTransformer$"),
            Pattern.compile("org\\.apache\\.commons\\.collections\\.functors\\.ChainedTransformer$"),
            Pattern.compile("org\\.apache\\.commons\\.collections\\.functors\\.ConstantTransformer$"),
            Pattern.compile("org\\.apache\\.commons\\.collections\\.functors\\.InstantiateTransformer$"),
            Pattern.compile("org\\.apache\\.commons\\.collections4\\.functors\\.InvokerTransformer$"),
            Pattern.compile("org\\.apache\\.commons\\.collections4\\.functors\\.ChainedTransformer$"),
            Pattern.compile("org\\.apache\\.commons\\.collections4\\.functors\\.ConstantTransformer$"),
            Pattern.compile("org\\.apache\\.commons\\.collections4\\.functors\\.InstantiateTransformer$"),
            Pattern.compile("org\\.apache\\.commons\\.collections4\\.comparators\\.TransformingComparator$"),
            Pattern.compile("org\\.apache\\.commons\\.fileupload\\.disk\\.DiskFileItem$"),
            Pattern.compile("org\\.apache\\.wicket\\.util\\.upload\\.DiskFileItem$"),
            Pattern.compile("org\\.codehaus\\.groovy\\.runtime\\.ConvertedClosure$"),
            Pattern.compile("org\\.codehaus\\.groovy\\.runtime\\.MethodClosure$"),
            Pattern.compile("org\\.hibernate\\.engine\\.spi\\.TypedValue$"),
            Pattern.compile("org\\.hibernate\\.tuple\\.component\\.AbstractComponentTuplizer$"),
            Pattern.compile("org\\.hibernate\\.tuple\\.component\\.PojoComponentTuplizer$"),
            Pattern.compile("org\\.hibernate\\.type\\.AbstractType$"),
            Pattern.compile("org\\.hibernate\\.type\\.ComponentType$"),
            Pattern.compile("org\\.hibernate\\.type\\.Type$"),
            Pattern.compile("com\\.sun\\.rowset\\.JdbcRowSetImpl$"),
            Pattern.compile("org\\.jboss\\.(weld\\.)?interceptor\\.builder\\.InterceptionModelBuilder$"),
            Pattern.compile("org\\.jboss\\.(weld\\.)?interceptor\\.builder\\.MethodReference$"),
            Pattern.compile("org\\.jboss\\.(weld\\.)?interceptor\\.proxy\\.DefaultInvocationContextFactory$"),
            Pattern.compile("org\\.jboss\\.(weld\\.)?interceptor\\.proxy\\.InterceptorMethodHandler$"),
            Pattern.compile("org\\.jboss\\.(weld\\.)?interceptor\\.reader\\.ClassMetadataInterceptorReference$"),
            Pattern.compile("org\\.jboss\\.(weld\\.)?interceptor\\.reader\\.DefaultMethodMetadata$"),
            Pattern.compile("org\\.jboss\\.(weld\\.)?interceptor\\.reader\\.ReflectiveClassMetadata$"),
            Pattern.compile("org\\.jboss\\.(weld\\.)?interceptor\\.reader\\.SimpleInterceptorMetadata$"),
            Pattern.compile("org\\.jboss\\.(weld\\.)?interceptor\\.spi\\.instance\\.InterceptorInstantiator$"),
            Pattern.compile("org\\.jboss\\.(weld\\.)?interceptor\\.spi\\.metadata\\.InterceptorReference$"),
            Pattern.compile("org\\.jboss\\.(weld\\.)?interceptor\\.spi\\.metadata\\.MethodMetadata$"),
            Pattern.compile("org\\.jboss\\.(weld\\.)?interceptor\\.spi\\.model\\.InterceptionModel$"),
            Pattern.compile("org\\.jboss\\.(weld\\.)?interceptor\\.spi\\.model\\.InterceptionType$"),
            Pattern.compile("java\\.rmi\\.registry\\.Registry$"),
            Pattern.compile("java\\.rmi\\.server\\.ObjID$"),
            Pattern.compile("java\\.rmi\\.server\\.RemoteObjectInvocationHandler$"),
            Pattern.compile("net\\.sf\\.json\\.JSONObject$"),
            Pattern.compile("javax\\.xml\\.transform\\.Templates$"),
            Pattern.compile("org\\.python\\.core\\.PyObject$"),
            Pattern.compile("org\\.python\\.core\\.PyBytecode$"),
            Pattern.compile("org\\.python\\.core\\.PyFunction$"),
            Pattern.compile("org\\.mozilla\\.javascript\\..*$"),
            Pattern.compile("org\\.apache\\.myfaces\\.context\\.servlet\\.FacesContextImpl$"),
            Pattern.compile("org\\.apache\\.myfaces\\.context\\.servlet\\.FacesContextImplBase$"),
            Pattern.compile("org\\.apache\\.myfaces\\.el\\.CompositeELResolver$"),
            Pattern.compile("org\\.apache\\.myfaces\\.el\\.unified\\.FacesELContext$"),
            Pattern.compile("org\\.apache\\.myfaces\\.view\\.facelets\\.el\\.ValueExpressionMethodExpression$"),
            Pattern.compile("com\\.sun\\.syndication\\.feed\\.impl\\.ObjectBean$"),
            Pattern.compile("org\\.springframework\\.beans\\.factory\\.ObjectFactory$"),
            Pattern.compile("org\\.springframework\\.core\\.SerializableTypeWrapper\\$MethodInvokeTypeProvider$"),
            Pattern.compile("org\\.springframework\\.aop\\.framework\\.AdvisedSupport$"),
            Pattern.compile("org\\.springframework\\.aop\\.target\\.SingletonTargetSource$"),
            Pattern.compile("org\\.springframework\\.aop\\.framework\\.JdkDynamicAopProxy$"),
            Pattern.compile("org\\.springframework\\.core\\.SerializableTypeWrapper\\$TypeProvider$"),
            Pattern.compile("java\\.util\\.PriorityQueue$"),
            Pattern.compile("java\\.lang\\.reflect\\.Proxy$"),
            Pattern.compile("java\\.util\\.Comparator$"),
            Pattern.compile("org\\.reflections\\.Reflections$"),

            Pattern.compile("com\\.sun\\.org\\.apache\\.xalan\\.internal\\.xsltc\\.trax\\.TemplatesImpl$"),
    };
    private boolean useWhiteList;


    /**
     * @param inputStream The original inputStream
     * @throws IOException
     */
    public LookAheadObjectInputStream(InputStream inputStream) throws IOException {
        this(inputStream, true);
    }

    /**
     * @param inputStream The original inputStream
     * @throws IOException
     */
    public LookAheadObjectInputStream(InputStream inputStream, boolean useWhiteList) throws IOException {
        super(inputStream);

        this.useWhiteList = useWhiteList;
    }


    @Override
    protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
        String name = desc.getName();
        // log.error("resolveClass " + name);

        for (Pattern pattern : blackList) {
            if (pattern.matcher(name).find()) {
                String msg = "We received a blacklisted class at Java deserialization: '" + name + "'" + "(regex pattern: " + pattern.toString() + ")";
                log.error(msg);
                throw new InvalidClassException(msg);
            }
        }

        if (useWhiteList) {
            boolean whiteListed = false;
            for (Pattern pattern : whiteListP2PNetwork) {
                if (pattern.matcher(name).find())
                    whiteListed = true;
            }
            if (!whiteListed) {
                String msg = "We received a non-whitelisted class at Java deserialization: '" + name + "'";
                log.error(msg);
                throw new InvalidClassException(msg);
            }
        }

        return super.resolveClass(desc);
    }
}