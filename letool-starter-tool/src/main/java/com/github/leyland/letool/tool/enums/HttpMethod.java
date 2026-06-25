package com.github.leyland.letool.tool.enums;

/**
 * HTTP 请求方法枚举——涵盖 RFC 7231 定义的常用 HTTP 方法.
 *
 * <p>用于 {@link com.github.leyland.letool.tool.http.HttpUtil} 的链式 API 和内部引擎路由.</p>
 *
 * <h3>各方法说明</h3>
 * <table>
 *   <tr><th>枚举值</th><th>HTTP 方法</th><th>典型用途</th></tr>
 *   <tr><td>{@code GET}</td>    <td>GET</td>    <td>查询资源</td></tr>
 *   <tr><td>{@code POST}</td>   <td>POST</td>   <td>创建资源</td></tr>
 *   <tr><td>{@code PUT}</td>    <td>PUT</td>    <td>全量更新资源</td></tr>
 *   <tr><td>{@code DELETE}</td> <td>DELETE</td> <td>删除资源</td></tr>
 *   <tr><td>{@code PATCH}</td>  <td>PATCH</td>  <td>部分更新资源</td></tr>
 *   <tr><td>{@code HEAD}</td>   <td>HEAD</td>   <td>获取响应头（无响应体）</td></tr>
 *   <tr><td>{@code OPTIONS}</td><td>OPTIONS</td><td>查询支持的方法</td></tr>
 *   <tr><td>{@code TRACE}</td>  <td>TRACE</td>  <td>诊断/回显请求</td></tr>
 * </table>
 */
public enum HttpMethod {
    GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS, TRACE
}
