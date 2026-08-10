import axios from 'axios'
import { API_BASE } from '../config/api.js'

/** Axios 实例，用于非流式 API 请求 */
const request = axios.create({
  baseURL: API_BASE,
  timeout: 30000,
})

export default request
